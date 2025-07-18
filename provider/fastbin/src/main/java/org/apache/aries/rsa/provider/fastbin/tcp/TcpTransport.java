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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.io.ProtocolCodec;
import org.apache.aries.rsa.provider.fastbin.io.Transport;
import org.apache.aries.rsa.provider.fastbin.io.TransportListener;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpTransport implements Transport {

    private static final Logger LOG = LoggerFactory.getLogger(TcpTransport.class);

    protected State _serviceState = new CREATED();

    protected Map<String, Object> socketOptions;

    protected URI remoteLocation;
    protected URI localLocation;
    protected TransportListener listener;
    protected String remoteAddress;
    protected ProtocolCodec codec;

    protected SocketChannel channel;

    protected SocketState socketState = new DISCONNECTED();

    protected DispatchQueue dispatchQueue;
    private DispatchSource readSource;
    private DispatchSource writeSource;

    protected boolean useLocalHost = true;

    int max_read_rate;
    int max_write_rate;
    protected RateLimitingChannel rateLimitingChannel;

    boolean drained = true;

    private final Runnable CANCEL_HANDLER = new Runnable() {
        public void run() {
            socketState.onCanceled();
        }
    };

    public final void start() {
        start(null);
    }

    public final void stop() {
        stop(null);
    }

    public final void start(final Runnable onCompleted) {
        queue().execute(new Runnable() {
            public void run() {
                if (_serviceState.isCreated() || _serviceState.isStopped()) {
                    final STARTING state = new STARTING();
                    state.add(onCompleted);
                    _serviceState = state;
                    _start(new Runnable() {
                        public void run() {
                            _serviceState = new STARTED();
                            state.done();
                        }
                    });
                } else if (_serviceState.isStarting()) {
                    _serviceState.add(onCompleted);
                } else if (_serviceState.isStarted()) {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                } else {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                    LOG.error("start should not be called from state: {}", _serviceState);
                }
            }
        });
    }

    public final void stop(final Runnable onCompleted) {
        queue().execute(new Runnable() {
            public void run() {
                if (_serviceState instanceof STARTED) {
                    final STOPPING state = new STOPPING();
                    state.add(onCompleted);
                    _serviceState = state;
                    _stop(new Runnable() {
                        public void run() {
                            _serviceState = new STOPPED();
                            state.done();
                        }
                    });
                } else if (_serviceState instanceof STOPPING) {
                    _serviceState.add(onCompleted);
                } else if (_serviceState instanceof STOPPED) {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                } else {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                    LOG.error("stop should not be called from state: {}", _serviceState);
                }
            }
        });
    }

    protected State getServiceState() {
        return _serviceState;
    }

    public void connected(SocketChannel channel) throws IOException, Exception {
        this.channel = channel;

        if( codec !=null ) {
            initializeCodec();
        }

        this.channel.configureBlocking(false);
        this.remoteAddress = channel.socket().getRemoteSocketAddress().toString();
        channel.socket().setSoLinger(true, 0);
        channel.socket().setTcpNoDelay(true);

        this.socketState = new CONNECTED();
    }

    protected void initializeCodec() {
        codec.setReadableByteChannel(readChannel());
        codec.setWritableByteChannel(writeChannel());
    }

    public void connecting(URI remoteLocation, URI localLocation) throws IOException, Exception {
        this.channel = SocketChannel.open();
        this.channel.configureBlocking(false);
        this.remoteLocation = remoteLocation;
        this.localLocation = localLocation;

        if (localLocation != null) {
            InetSocketAddress localAddress = new InetSocketAddress(InetAddress.getByName(localLocation.getHost()), localLocation.getPort());
            channel.socket().bind(localAddress);
        }

        String host = resolveHostName(remoteLocation.getHost());
        InetSocketAddress remoteAddress = new InetSocketAddress(host, remoteLocation.getPort());
        channel.connect(remoteAddress);
        this.socketState = new CONNECTING();
    }

    public DispatchQueue queue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue queue) {
        this.dispatchQueue = queue;
    }

    public void _start(Runnable onCompleted) {
        try {
            if (socketState.isConnecting()) {
                trace("connecting...");
                // this allows the connect to complete...
                readSource = Dispatch.createSource(channel, SelectionKey.OP_CONNECT, dispatchQueue);
                readSource.setEventHandler(new Runnable() {
                    public void run() {
                        if (!(getServiceState().isStarted())) {
                            return;
                        }
                        try {
                            trace("connected.");
                            channel.finishConnect();
                            readSource.setCancelHandler(null);
                            readSource.cancel();
                            readSource = null;
                            socketState = new CONNECTED();
                            onConnected();
                        } catch (IOException e) {
                            onTransportFailure(e);
                        }
                    }
                });
                readSource.setCancelHandler(CANCEL_HANDLER);
                readSource.resume();

            } else if (socketState.isConnected()) {
                dispatchQueue.execute(new Runnable() {
                    public void run() {
                        try {
                            trace("was connected.");
                            onConnected();
                        } catch (IOException e) {
                            onTransportFailure(e);
                        }
                    }
                });
            } else {
                System.err.println("cannot be started.  socket state is: "+socketState);
            }
        } finally {
            if( onCompleted!=null ) {
                onCompleted.run();
            }
        }
    }

    public void _stop(final Runnable onCompleted) {
        trace("stopping.. at state: "+socketState);
        socketState.onStop(onCompleted);
    }

    protected String resolveHostName(String host) throws UnknownHostException {
        try {
            if(isUseLocalHost()) {
                String localName = InetAddress.getLocalHost().getHostName();
                if (localName != null) {
                    if (localName.equals(host)) {
                        return "localhost";
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to resolve local host address: " + e.getClass() + " message: " + e.getMessage());
            LOG.debug("Stacktrace: ", e);
        }
        return host;
    }

    protected void onConnected() throws IOException {

        readSource = Dispatch.createSource(channel, SelectionKey.OP_READ, dispatchQueue);
        writeSource = Dispatch.createSource(channel, SelectionKey.OP_WRITE, dispatchQueue);

        readSource.setCancelHandler(CANCEL_HANDLER);
        writeSource.setCancelHandler(CANCEL_HANDLER);

        readSource.setEventHandler(new Runnable() {
            public void run() {
                drainInbound();
            }
        });
        writeSource.setEventHandler(new Runnable() {
            public void run() {
                drainOutbound();
            }
        });

        if( max_read_rate!=0 || max_write_rate!=0 ) {
            rateLimitingChannel = new RateLimitingChannel();
            scheduleRateAllowanceReset();
        }

        remoteAddress = channel.socket().getRemoteSocketAddress().toString();
        listener.onTransportConnected(this);
    }

    private void scheduleRateAllowanceReset() {
        dispatchQueue.executeAfter(1, TimeUnit.SECONDS, new Runnable(){
            public void run() {
                if (!socketState.isConnected()) {
                    return;
                }
                rateLimitingChannel.resetAllowance();
                scheduleRateAllowanceReset();
            }
        });
    }

    private void dispose() {
        if( readSource != null ) {
            readSource.cancel();
            readSource = null;
        }

        if( writeSource != null ) {
            writeSource.cancel();
            writeSource = null;
        }
        this.codec = null;
    }

    public void onTransportFailure(IOException error) {
        listener.onTransportFailure(this, error);
        socketState.onCanceled();
    }

    public boolean full() {
        return codec.full();
    }

    public boolean offer(Object command) {
        assert Dispatch.getCurrentQueue() == dispatchQueue;
        try {
            if (!socketState.isConnected()) {
                throw new IOException("Not connected.");
            }
            if (!getServiceState().isStarted()) {
                throw new IOException("Not running.");
            }

            ProtocolCodec.BufferState rc = codec.write(command);
            switch (rc ) {
                case FULL:
                    return false;
                default:
                    if( drained ) {
                        drained = false;
                        resumeWrite();
                    }
                    return true;
            }
        } catch (IOException e) {
            onTransportFailure(e);
            return false;
        }
    }

    /**
     *
     */
    protected void drainOutbound() {
        assert Dispatch.getCurrentQueue() == dispatchQueue;
        if (!getServiceState().isStarted() || !socketState.isConnected()) {
            return;
        }
        try {
            if( codec.flush() == ProtocolCodec.BufferState.WAS_EMPTY && flush() ) {
                if( !drained ) {
                    drained = true;
                    suspendWrite();
                    listener.onRefill(this);
                }
            }
        } catch (IOException e) {
            onTransportFailure(e);
        }
    }

    protected boolean flush() throws IOException {
        return true;
    }

    protected void drainInbound() {
        if (!getServiceState().isStarted() || readSource.isSuspended()) {
            return;
        }
        try {
            long initial = codec.getReadCounter();
            // Only process up to 64k worth of data at a time, so we can give
            // other connections a chance to process their requests.
            while( codec.getReadCounter() - initial < 1024 * 64 ) {
                Object command = codec.read();
                if ( command!=null ) {
                    try {
                        listener.onTransportCommand(this, command);
                    } catch (Throwable e) {
                        onTransportFailure(new IOException("Transport listener failure."));
                    }

                    // the transport may be suspended after processing a command.
                    if (getServiceState().isStopped() || readSource.isSuspended()) {
                        return;
                    }
                } else {
                    return;
                }
            }
        } catch (IOException e) {
            onTransportFailure(e);
        }
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void suspendRead() {
        if( isConnected() && readSource!=null ) {
            readSource.suspend();
        }
    }

    public void resumeRead() {
        if( isConnected() && readSource!=null ) {
            if( rateLimitingChannel!=null ) {
                rateLimitingChannel.resumeRead();
            } else {
                _resumeRead();
            }
        }
    }
    private void _resumeRead() {
        readSource.resume();
        dispatchQueue.execute(new Runnable(){
            public void run() {
                drainInbound();
            }
        });
    }

    protected void suspendWrite() {
        if( isConnected() && writeSource!=null ) {
            writeSource.suspend();
        }
    }
    protected void resumeWrite() {
        if( isConnected() && writeSource!=null ) {
            writeSource.resume();
            dispatchQueue.execute(new Runnable(){
                public void run() {
                    drainOutbound();
                }
            });
        }
    }

    public TransportListener getTransportListener() {
        return listener;
    }

    public void setTransportListener(TransportListener listener) {
        this.listener = listener;
    }

    public ProtocolCodec getProtocolCodec() {
        return codec;
    }

    public void setProtocolCodec(ProtocolCodec protocolCodec) {
        this.codec = protocolCodec;
        if( channel!=null && codec!=null ) {
            initializeCodec();
        }
    }

    public boolean isConnected() {
        return socketState.isConnected();
    }

    public boolean isDisposed() {
        return getServiceState().isStopped() || getServiceState().isStopping();
    }

    public void setSocketOptions(Map<String, Object> socketOptions) {
        this.socketOptions = socketOptions;
    }

    public boolean isUseLocalHost() {
        return useLocalHost;
    }

    /**
     * Sets whether 'localhost' or the actual local host name should be used to
     * make local connections. On some operating systems such as Macs it's not
     * possible to connect as the local host name so localhost is better.
     */
    public void setUseLocalHost(boolean useLocalHost) {
        this.useLocalHost = useLocalHost;
    }

    private void trace(String message) {
        if( LOG.isTraceEnabled() ) {
            final String label = dispatchQueue.getLabel();
            if( label !=null ) {
                LOG.trace("{} | {}", label, message);
            } else {
                LOG.trace(message);
            }
        }
    }

    public SocketChannel getSocketChannel() {
        return channel;
    }

    public ReadableByteChannel readChannel() {
        if(rateLimitingChannel!=null) {
            return rateLimitingChannel;
        } else {
            return channel;
        }
    }

    public WritableByteChannel writeChannel() {
        if(rateLimitingChannel!=null) {
            return rateLimitingChannel;
        } else {
            return channel;
        }
    }

    public int getMax_read_rate() {
        return max_read_rate;
    }

    public void setMax_read_rate(int max_read_rate) {
        this.max_read_rate = max_read_rate;
    }

    public int getMax_write_rate() {
        return max_write_rate;
    }

    public void setMax_write_rate(int max_write_rate) {
        this.max_write_rate = max_write_rate;
    }

    class RateLimitingChannel implements ReadableByteChannel, WritableByteChannel {

        int read_allowance = max_read_rate;
        boolean read_suspended = false;
        int read_resume_counter = 0;
        int write_allowance = max_write_rate;
        boolean write_suspended = false;

        public void resetAllowance() {
            if( read_allowance != max_read_rate || write_allowance != max_write_rate) {
                read_allowance = max_read_rate;
                write_allowance = max_write_rate;
                if( write_suspended ) {
                    write_suspended = false;
                    resumeWrite();
                }
                if( read_suspended ) {
                    read_suspended = false;
                    resumeRead();
                    for( int i = 0; i < read_resume_counter ; i++ ) {
                        resumeRead();
                    }
                }
            }
        }

        public int read(ByteBuffer dst) throws IOException {
            if( max_read_rate==0 ) {
                return channel.read(dst);
            } else {
                int remaining = dst.remaining();
                if( read_allowance ==0 || remaining ==0 ) {
                    return 0;
                }

                int reduction = 0;
                if( remaining > read_allowance) {
                    reduction = remaining - read_allowance;
                    dst.limit(dst.limit() - reduction);
                }
                int rc;
                try {
                    rc = channel.read(dst);
                    read_allowance -= rc;
                } finally {
                    if( reduction!=0 ) {
                        if( dst.remaining() == 0 ) {
                            // we need to suspend the read now until we get
                            // a new allowance...
                            readSource.suspend();
                            read_suspended = true;
                        }
                        dst.limit(dst.limit() + reduction);
                    }
                }
                return rc;
            }
        }

        public int write(ByteBuffer src) throws IOException {
            if( max_write_rate==0 ) {
                return channel.write(src);
            } else {
                int remaining = src.remaining();
                if( write_allowance ==0 || remaining ==0 ) {
                    return 0;
                }

                int reduction = 0;
                if( remaining > write_allowance) {
                    reduction = remaining - write_allowance;
                    src.limit(src.limit() - reduction);
                }
                int rc;
                try {
                    rc = channel.write(src);
                    write_allowance -= rc;
                } finally {
                    if( reduction!=0 ) {
                        if( src.remaining() == 0 ) {
                            // we need to suspend the read now until we get
                            // a new allowance...
                            write_suspended = true;
                            suspendWrite();
                        }
                        src.limit(src.limit() + reduction);
                    }
                }
                return rc;
            }
        }

        public boolean isOpen() {
            return channel.isOpen();
        }

        public void close() throws IOException {
            channel.close();
        }

        public void resumeRead() {
            if( read_suspended ) {
                read_resume_counter += 1;
            } else {
                _resumeRead();
            }
        }

    }

    //
    // Transport states
    //

    public abstract static class State {
        LinkedList<Runnable> callbacks = new LinkedList<>();

        void add(Runnable r) {
            if (r != null) {
                callbacks.add(r);
            }
        }

        void done() {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        }

        public String toString() {
            return getClass().getSimpleName();
        }

        boolean is(Class<? extends State> clazz) {
            return getClass() == clazz;
        }

        public boolean isCreated() {
            return is(CREATED.class);
        }

        public boolean isStarting() {
            return is(STARTING.class);
        }

        public boolean isStarted() {
            return is(STARTED.class);
        }

        public boolean isStopping() {
            return is(STOPPING.class);
        }

        public boolean isStopped() {
            return is(STOPPED.class);
        }
    }

    public static final class CREATED extends State {
    }

    public static final class STARTING extends State {
    }

    public static final class STARTED extends State {
    }

    public static final class STOPPING extends State {
    }

    public static final class STOPPED extends State {
    }

    //
    // Socket states
    //

    abstract static class SocketState {
        void onStop(Runnable onCompleted) {
        }
        void onCanceled() {
        }
        boolean is(Class<? extends SocketState> clazz) {
            return getClass()==clazz;
        }
        boolean isConnecting() {
            return is(CONNECTING.class);
        }
        boolean isConnected() {
            return is(CONNECTED.class);
        }
    }

    class DISCONNECTED extends SocketState {
    }

    class CONNECTING extends SocketState {
        void onStop(Runnable onCompleted) {
            trace("CONNECTING.onStop");
            CANCELING state = new CANCELING();
            socketState = state;
            state.onStop(onCompleted);
        }
        void onCanceled() {
            trace("CONNECTING.onCanceled");
            CANCELING state = new CANCELING();
            socketState = state;
            state.onCanceled();
        }
    }

    class CONNECTED extends SocketState {
        void onStop(Runnable onCompleted) {
            trace("CONNECTED.onStop");
            CANCELING state = new CANCELING();
            socketState = state;
            state.add(createDisconnectTask());
            state.onStop(onCompleted);
        }
        void onCanceled() {
            trace("CONNECTED.onCanceled");
            CANCELING state = new CANCELING();
            socketState = state;
            state.add(createDisconnectTask());
            state.onCanceled();
        }
        Runnable createDisconnectTask() {
            return new Runnable(){
                public void run() {
                    listener.onTransportDisconnected(TcpTransport.this);
                }
            };
        }
    }

    class CANCELING extends SocketState {
        private LinkedList<Runnable> runnables = new LinkedList<>();
        private int remaining;
        private boolean dispose;

        public CANCELING() {
            if( readSource!=null ) {
                remaining++;
                readSource.cancel();
            }
            if( writeSource!=null ) {
                remaining++;
                writeSource.cancel();
            }
        }
        void onStop(Runnable onCompleted) {
            trace("CANCELING.onCompleted");
            add(onCompleted);
            dispose = true;
        }
        void add(Runnable onCompleted) {
            if( onCompleted!=null ) {
                runnables.add(onCompleted);
            }
        }
        void onCanceled() {
            trace("CANCELING.onCanceled");
            remaining--;
            if( remaining!=0 ) {
                return;
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            socketState = new CANCELED(dispose);
            for (Runnable runnable : runnables) {
                runnable.run();
            }
            if (dispose) {
                dispose();
            }
        }
    }

    class CANCELED extends SocketState {
        private boolean disposed;

        public CANCELED(boolean disposed) {
            this.disposed = disposed;
        }

        void onStop(Runnable onCompleted) {
            trace("CANCELED.onStop");
            if( !disposed ) {
                disposed = true;
                dispose();
            }
            onCompleted.run();
        }
    }

}
