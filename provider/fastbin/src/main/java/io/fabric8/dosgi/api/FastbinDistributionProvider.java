/**
 *  Copyright 2016 SEEBURGER AG
 *
 *  SEEBURGER licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.dosgi.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.dosgi.io.ClientInvoker;
import io.fabric8.dosgi.io.ServerInvoker;
import io.fabric8.dosgi.tcp.ClientInvokerImpl;
import io.fabric8.dosgi.tcp.ServerInvokerImpl;

@Component(enabled=true)
@Service
@Properties({
    @Property(name = RemoteConstants.REMOTE_INTENTS_SUPPORTED, value = {""}),
    @Property(name = RemoteConstants.REMOTE_CONFIGS_SUPPORTED, value = {FastbinDistributionProvider.CONFIG_NAME})})
public class FastbinDistributionProvider implements DistributionProvider {

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
     * the port to bind the server socket to. Defaults to 9000
     */
    public static final String PORT = "fastbin.port";

    private static final Logger LOG = LoggerFactory.getLogger(FastbinDistributionProvider.class);

    public static final int PROTOCOL_VERSION = 1;
    public static final String PROTOCOL_VERSION_PROPERTY = "fastbin.protocol.version";


    private ServerInvoker server;
    private ClientInvoker client;
    private DispatchQueue queue;
    private ConcurrentHashMap<String, SerializationStrategy> serializationStrategies;
    private BundleContext bundleContext;

    @SuppressWarnings("rawtypes")
    @Activate
    public void activate(BundleContext context, Map<String, ?> dictionary) {
        this.bundleContext = context;
        Map<String, Object> config = new HashMap<String, Object>();
        config.putAll(dictionary);

        this.queue = Dispatch.createQueue();
        this.serializationStrategies = new ConcurrentHashMap<String, SerializationStrategy>();
        int port = Integer.parseInt(config.getOrDefault(PORT, System.getProperty(PORT,"9000")).toString());
        String publicHost = (String)config.get(SERVER_ADDRESS);
        try {
            if(publicHost==null)
            {
                publicHost = Inet4Address.getLocalHost().getCanonicalHostName();
                LOG.info("public server address (fastbin.address) not set. Using {} as default",publicHost);
            }
            server = new ServerInvokerImpl("tcp://"+publicHost+":"+port, queue, serializationStrategies);
            server.start();
            client = new ClientInvokerImpl(queue, serializationStrategies);
            client.start();
        } catch (Exception e) {
            LOG.error("Failed to start the tcp server",e);
        }
    }

    @Deactivate
    public void deactivate() {
        server.stop();
        client.stop();
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[] { CONFIG_NAME };
    }


    @Override
    public Endpoint exportService(Object serviceO, BundleContext serviceContext, Map<String, Object> effectiveProperties, Class[] exportedInterfaces)
    {
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

