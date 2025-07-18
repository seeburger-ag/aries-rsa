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
package org.apache.aries.rsa.provider.fastbin;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.rsa.provider.fastbin.api.FastbinEndpoint;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.tcp.ClientInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.tcp.ServerInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.tcp.TcpTransportServer;
import org.apache.aries.rsa.provider.fastbin.util.UuidGenerator;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class FastBinProvider implements DistributionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FastBinProvider.class);

    /**
     * the name of the configuration type (service.exported.configs)
     */
    public static final String CONFIG_NAME = "aries.fastbin";
    /**
     * the endpoint address of the exported service. If left empty a generated endpoint id will be used
     */
    public static final String ENDPOINT_ADDRESS = "fastbin.endpoint.address";
    /**
     * the server address to connect to
     */
    public static final String SERVER_ADDRESS = "fastbin.address";
    /**
     * the bind address to bind the socket to. Defaults to <code>{@link #SERVER_ADDRESS}</code>
     */
    public static final String SERVER_BIND_ADDRESS = "fastbin.bind.address";
    /**
     * the port to bind the server socket to. Defaults to 4000
     */
    public static final String PORT = "fastbin.port";

    /**
     * the tcp request timeout in milliseconds. Defaults to 20s
     */
    public static final String TIMEOUT = "fastbin.timeout";


    public static final int PROTOCOL_VERSION = 1;
    public static final String PROTOCOL_VERSION_PROPERTY = "fastbin.protocol.version";


    private ServerInvoker server;
    private ClientInvoker client;
    private DispatchQueue queue;
    private ConcurrentHashMap<String, SerializationStrategy> serializationStrategies;

    private BundleContext bundleContext;
    private volatile AtomicBoolean started = new AtomicBoolean(false);
    private ServiceRegistration registration;


    public void activate(BundleContext context, Map<String, ?> dictionary) {
        this.bundleContext = context;
        Map<String, Object> config = new HashMap<>();
        config.putAll(dictionary);

        started.set(false);
        this.queue = Dispatch.createQueue();
        this.serializationStrategies = new ConcurrentHashMap<>();
        int port = Integer.parseInt(config.getOrDefault(PORT, System.getProperty(PORT,"4000")).toString());
        long timeout = Long.parseLong(config.getOrDefault(TIMEOUT, System.getProperty(TIMEOUT,String.valueOf(ClientInvokerImpl.DEFAULT_TIMEOUT))).toString());
        String publicHost = (String)config.getOrDefault(SERVER_ADDRESS, System.getProperty(SERVER_ADDRESS, null));
        try {
            if(publicHost==null)
            {
                publicHost = Inet4Address.getLocalHost().getCanonicalHostName();
                LOG.info("public server address (fastbin.address) not set. Using {} as default",publicHost);
            }
            String bindAddress = (String)config.getOrDefault(SERVER_BIND_ADDRESS, System.getProperty(SERVER_BIND_ADDRESS));
            String uri = "tcp://"+publicHost+":"+port;
            if(bindAddress!=null)
            {
                uri += "?"+TcpTransportServer.BIND_ADDRESS_QUERY_PARAM+"="+bindAddress;
            }
            server = new ServerInvokerImpl(uri, queue, serializationStrategies);
            client = new ClientInvokerImpl(queue, timeout, serializationStrategies);
            client.start();
        } catch (Exception e) {
            LOG.error("Failed to start the tcp client",e);
        }
        registration = context.registerService(DistributionProvider.class.getName(), this, new Hashtable<>(dictionary));
    }


    public void deactivate() {
        if(registration!=null)
            registration.unregister();
        server.stop();
        client.stop();
    }

    public ClientInvoker getClient() {
        return client;
    }

    public ServerInvoker getServer() {
        return server;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[] {CONFIG_NAME};
    }

    @Override
    public Endpoint exportService(Object serviceO, BundleContext serviceContext, Map<String, Object> effectiveProperties, Class[] exportedInterfaces)
    {
        if(started.compareAndSet(false, true))
        {
            try
            {
                server.start();
            }
            catch (Exception e)
            {
                LOG.error("Failed to start the tcp server",e);
                started.set(false);
            }
        }
        effectiveProperties.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, getSupportedTypes());
        return new FastbinEndpoint(server,effectiveProperties,serviceO);
    }

    @Override
    public Object importEndpoint(ClassLoader cl, BundleContext consumerContext, Class[] interfaces, EndpointDescription endpoint)
    {
        String callID = (String) endpoint.getProperties().get(RemoteConstants.ENDPOINT_ID);
        int protocolVersion = Integer.parseInt(endpoint.getProperties().getOrDefault(PROTOCOL_VERSION_PROPERTY,PROTOCOL_VERSION).toString());
        // use the highest version that is available on both server and client.
        protocolVersion = Math.min(protocolVersion, PROTOCOL_VERSION);
        InvocationHandler invocationHandler = client.getProxy((String) endpoint.getProperties().get(SERVER_ADDRESS), callID, cl, protocolVersion);
        return Proxy.newProxyInstance(cl, interfaces,invocationHandler);
    }

}
