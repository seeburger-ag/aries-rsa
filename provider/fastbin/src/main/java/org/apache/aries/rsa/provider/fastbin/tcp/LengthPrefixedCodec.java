/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.provider.fastbin.tcp;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.aries.rsa.provider.fastbin.io.ProtocolCodec;
import org.fusesource.hawtbuf.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LengthPrefixedCodec implements ProtocolCodec {

    protected static final Logger LOG = LoggerFactory.getLogger(LengthPrefixedCodec.class);

    /** prevent DOS attacks in case a very large size field is sent. Default is 10MB */
    private static final int MAX_PACKET_SIZE = Integer.getInteger("aries.fastbin.max.packet.bytes", 1024 * 1024 * 10) <= 0 ? Integer.MAX_VALUE : Integer.getInteger("aries.fastbin.max.packet.bytes", 1024 * 1024 * 10);
    // System property to control dumping behavior for packets exceeding the maximum size
    private static final boolean DUMP_EXCEEDING_PACKETS = Boolean.getBoolean("aries.fastbin.dump.exceeding.packets");
    /** prevent DOS attacks in case a very large dump is requested. Default is 1MB */
    private static final long DUMP_MAX_SIZE = Long.getLong("aries.fastbin.dump.max.bytes", 1024 * 1024) <= 0 ? Long.MAX_VALUE : Long.getLong("aries.fastbin.dump.max.bytes", 1024 * 1024);

    final int write_buffer_size = 1024 * 64;
    long write_counter = 0L;
    WritableByteChannel write_channel;
    final Queue<ByteBuffer> next_write_buffers = new LinkedList<>();
    int next_write_size = 0;

    public boolean full() {
        return false;
    }

    protected boolean empty() {
        if (next_write_size > 0) {
            return false;
        }
        if (!next_write_buffers.isEmpty()) {
            for (ByteBuffer b : next_write_buffers) {
                if (b.remaining() > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setWritableByteChannel(WritableByteChannel channel) {
        this.write_channel = channel;
        if (channel instanceof SocketChannel) {
            try {
                ((SocketChannel) channel).socket().setSendBufferSize(write_buffer_size);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
    }

    public BufferState write(Object value) throws IOException {
        if (full()) {
            return BufferState.FULL;
        } else {
            boolean wasEmpty = empty();
            Buffer buffer = (Buffer) value;
            next_write_size += buffer.length;
            next_write_buffers.add(buffer.toByteBuffer());
            return wasEmpty ? BufferState.WAS_EMPTY : BufferState.NOT_EMPTY;
        }
    }

    public BufferState flush() throws IOException {
        final long writeCounterBeforeFlush = write_counter;
        while(!next_write_buffers.isEmpty()) {
            final ByteBuffer nextBuffer = next_write_buffers.peek();
            if (nextBuffer.remaining() < 1) {
                next_write_buffers.remove();
                continue;
            }
            int bytesWritten = write_channel.write(nextBuffer);
            write_counter += bytesWritten;
            next_write_size -= bytesWritten;
            if (nextBuffer.remaining() > 0) {
                break;
            }
        }
        if (empty()) {
            if (writeCounterBeforeFlush == write_counter) {
                return BufferState.WAS_EMPTY;
            } else {
                return BufferState.EMPTY;
            }
        }
        return BufferState.NOT_EMPTY;
    }

    public long getWriteCounter() {
        return write_counter;
    }

    long read_counter = 0L;
    int read_buffer_size = 1024 * 64;
    ReadableByteChannel read_channel = null;
    ByteBuffer read_buffer = ByteBuffer.allocate(4);

    public void setReadableByteChannel(ReadableByteChannel channel) {
        read_channel = channel;
        if (channel instanceof SocketChannel) {
            try {
                ((SocketChannel) channel).socket().setReceiveBufferSize(read_buffer_size);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
    }

    public Object read() throws IOException {
        while(true) {
            if( read_buffer.remaining()!=0 ) {
                // keep reading from the channel until we fill the read buffer
                int count = read_channel.read(read_buffer);
                if (count == -1) {
                    throw new EOFException("Peer disconnected");
                } else if (count == 0) {
                    return null;
                }
                read_counter += count;
            } else {
                //read buffer is full... interpret it
                read_buffer.flip();

                if( read_buffer.capacity() == 4 ) {
                    // Finding out the
                    int size = read_buffer.getInt(0);
                    if( size < 4 ) {
                        throw new ProtocolException("Expecting a size greater than 3");
                    }
                    else if( size > MAX_PACKET_SIZE ) {
                        // Get first 1kB for logging
                        byte[] firstBytes = getFirstKiloByteForLogging();
                        dumpExceedingPacket(size, firstBytes);
                        throw new ProtocolException("Packet length was declared as " + size + " but at most " + MAX_PACKET_SIZE
                                                    + " bytes are allowed. You can configure this limit with the system property aries.fastbin.max.packet.bytes"
                                                    + "\n First 1kB of the packet:\n" + new String(firstBytes, StandardCharsets.UTF_8));
                    }
                    if( size == 4 ) {
                        // weird... empty frame... guess it could happen.
                        Buffer rc = new Buffer(read_buffer);
                        read_buffer = ByteBuffer.allocate(4);
                        return rc;
                    } else {
                        // Resize to the right size... this resumes the reads
                        ByteBuffer next = ByteBuffer.allocate(size);
                        next.putInt(size);
                        read_buffer = next;
                    }
                } else {
                    // finish loading the rest of the buffer
                    Buffer rc = new Buffer(read_buffer);
                    read_buffer = ByteBuffer.allocate(4);
                    return rc;
                }
            }
        }
    }


    private byte[] getFirstKiloByteForLogging() throws IOException {
        ByteArrayOutputStream baOs = new ByteArrayOutputStream(1024);
        int readBytes;
        try (WritableByteChannel channel = Channels.newChannel(baOs))
        {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            // Read the first 1kB of data - ignoring that it might read less
            readBytes = read_channel.read(buffer);
            // Prepare the buffer to be drained
            buffer.flip();
            // Make sure that the buffer was fully drained
            while (buffer.hasRemaining())
            {
                channel.write(buffer);
            }
            // Make the buffer empty, ready for filling
            buffer.clear();
        }
        catch (IOException ioEx) {
            return ("No details available - " + ioEx).getBytes(StandardCharsets.UTF_8);
        }
        return baOs.toByteArray();
    }


    private void dumpExceedingPacket(int size, byte[] firstBytes) {
        if (!DUMP_EXCEEDING_PACKETS) {
            return;
        }

        Path dumpPath = Paths.get(System.currentTimeMillis() + "_aries-rsa-dump.bin");
        boolean truncated = false;
        try (WritableByteChannel channel = Channels.newChannel(Files.newOutputStream(dumpPath))) {
            // write the already read bytes to the file
            channel.write(ByteBuffer.wrap(firstBytes));
            ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
            long totalReadBytes = 0;
            while (true)
            {
                int readBytes = read_channel.read(buffer);
                if (readBytes == -1) break;
                totalReadBytes += readBytes;
                // Prepare the buffer to be drained
                buffer.flip();
                // Make sure that the buffer was fully drained
                while (buffer.hasRemaining())
                {
                    channel.write(buffer);
                }
                // Make the buffer empty, ready for filling
                buffer.clear();

                if (totalReadBytes > DUMP_MAX_SIZE) {
                    // If we read more than the max dump size, stop dumping
                    channel.write(StandardCharsets.UTF_8.encode("\n\nDumping truncated after " + DUMP_MAX_SIZE + " bytes"));
                    truncated = true;
                    break;
                }
            }
            LOG.warn("Request with exceeded packet length of {} bytes was dumped {} to {}",
                     size, (truncated ? ("truncated to " + DUMP_MAX_SIZE + " bytes") : "completely"), dumpPath.toAbsolutePath());
        }
        catch (IOException ioEx) {
            LOG.warn("Request with exceeded packet length of {} bytes could not be dumped!", size, ioEx);
        }
    }


    public long getReadCounter() {
        return read_counter;
    }

}
