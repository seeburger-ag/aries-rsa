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
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.provider.fastbin.io.TransportAcceptListener;
import org.apache.aries.rsa.provider.fastbin.io.TransportServer;
import org.apache.aries.rsa.provider.fastbin.util.IntrospectionSupport;
import org.apache.aries.rsa.provider.fastbin.util.URISupport;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TCP based transport server
 *
 */

public class TcpTransportServer implements TransportServer {

    private static final Logger LOG = LoggerFactory.getLogger(TcpTransportServer.class);
    private final String bindScheme;
    private final InetSocketAddress bindAddress;

    private int backlog = 100;
    private Map<String, Object> transportOptions;

    private ServerSocketChannel channel;
    private TransportAcceptListener listener;
    private DispatchQueue dispatchQueue;
    private DispatchSource acceptSource;
    private String connectAddress;

    /** query param for the location uri if the bind address is different than the public server address */
    public static final String BIND_ADDRESS_QUERY_PARAM = "bindAddress";

    public TcpTransportServer(URI location) throws UnknownHostException {
        bindScheme = location.getScheme();
        String host = location.getHost();
        connectAddress = location.getScheme() + "://";
        if(host==null || host.length()==0)
        {
            host = "::";
            connectAddress += resolveHostName();
        }
        else
        {
            connectAddress += host;
        }

        Map<String, String> options = Collections.emptyMap();
        try
        {
            options = new HashMap<String, String>(URISupport.parseParameters(location));
        }
        catch (URISyntaxException e)
        {
            LOG.error("Failed to parse location URI "+location,e);
        }
        if(options.containsKey(BIND_ADDRESS_QUERY_PARAM))
        {
            this.bindAddress = new InetSocketAddress(options.get(BIND_ADDRESS_QUERY_PARAM), location.getPort());
        }
        else
        {
            this.bindAddress = new InetSocketAddress(InetAddress.getByName(host), location.getPort());
        }
    }

    public void setAcceptListener(TransportAcceptListener listener) {
        this.listener = listener;
    }

    public InetSocketAddress getSocketAddress() {
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        this.dispatchQueue = dispatchQueue;
    }

    public void suspend() {
        acceptSource.suspend();
    }

    public void resume() {
        acceptSource.resume();
    }

    public void start() throws Exception {
        start(null);
    }
    public void start(Runnable onCompleted) throws Exception {

        try {
            channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(bindAddress, backlog);
        } catch (IOException e) {
            throw new IOException("Failed to bind to server socket: " + bindAddress + " due to: " + e, e);
        }

        acceptSource = Dispatch.createSource(channel, SelectionKey.OP_ACCEPT, dispatchQueue);
        acceptSource.setEventHandler(new Runnable() {
            public void run() {
                try {
                    SocketChannel client = channel.accept();
                    while( client!=null ) {
                        handleSocket(client);
                        client = channel.accept();
                    }
                } catch (Exception e) {
                    listener.onAcceptError(TcpTransportServer.this, e);
                }
            }
        });
        acceptSource.setCancelHandler(new Runnable() {
            public void run() {
                try {
                    channel.close();
                } catch (IOException e) {
                }
            }
        });
        acceptSource.resume();
        if( onCompleted!=null ) {
            dispatchQueue.execute(onCompleted);
        }
    }

    public String getBoundAddress() {
        try {
            return new URI(bindScheme, null, bindAddress.getAddress().getHostAddress(), channel.socket().getLocalPort(), null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getConnectAddress() {
        int port = bindAddress.getPort() <=0 ? channel.socket().getLocalPort() : bindAddress.getPort();
        return connectAddress + ":" + port;
    }

    protected String resolveHostName() {
        String result;
        if (bindAddress.getAddress().isAnyLocalAddress()) {
            // make it more human-readable and useful, an alternative to 0.0.0.0
            try {
                result = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                result = "localhost";
            }
        } else {
            result = bindAddress.getAddress().getCanonicalHostName();
        }
        return result;
    }

    public void stop() {
        stop(null);
    }
    public void stop(final Runnable onCompleted) {
        if(acceptSource==null) {
            if( onCompleted!=null ) {
                onCompleted.run();
            }
            return;
        }
        if( acceptSource.isCanceled() ) {
            onCompleted.run();
        } else {
            acceptSource.setCancelHandler(new Runnable() {
                public void run() {
                    try {
                        channel.close();
                    } catch (IOException e) {
                    }
                    if( onCompleted!=null ) {
                        onCompleted.run();
                    }
                }
            });
            acceptSource.cancel();
        }
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    protected final void handleSocket(SocketChannel socket) throws Exception {
        HashMap<String, Object> options = new HashMap<>();
//      options.put("maxInactivityDuration", Long.valueOf(maxInactivityDuration));
//      options.put("maxInactivityDurationInitialDelay", Long.valueOf(maxInactivityDurationInitialDelay));
//      options.put("trace", Boolean.valueOf(trace));
//      options.put("soTimeout", Integer.valueOf(soTimeout));
//      options.put("socketBufferSize", Integer.valueOf(socketBufferSize));
//      options.put("connectionTimeout", Integer.valueOf(connectionTimeout));
//      options.put("dynamicManagement", Boolean.valueOf(dynamicManagement));
//      options.put("startLogging", Boolean.valueOf(startLogging));

        TcpTransport transport = createTransport(socket, options);
        listener.onAccept(this, transport);
    }

    protected TcpTransport createTransport(SocketChannel socketChannel, HashMap<String, Object> options) throws Exception {
        TcpTransport transport = createTransport();
        transport.connected(socketChannel);
        if( options!=null ) {
            IntrospectionSupport.setProperties(transport, options);
        }
        if (transportOptions != null) {
            IntrospectionSupport.setProperties(transport, transportOptions);
        }
        return transport;
    }

    protected TcpTransport createTransport() {
        return new TcpTransport();
    }

    public void setTransportOption(Map<String, Object> transportOptions) {
        this.transportOptions = transportOptions;
    }

    /**
     * @return pretty print of this
     */
    public String toString() {
        return getBoundAddress();
    }

}
