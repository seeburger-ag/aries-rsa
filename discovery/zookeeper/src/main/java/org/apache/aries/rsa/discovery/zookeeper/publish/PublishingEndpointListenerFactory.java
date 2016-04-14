/**
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
package org.apache.aries.rsa.discovery.zookeeper.publish;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.apache.aries.rsa.discovery.zookeeper.ZooKeeperDiscovery;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates local EndpointListeners that publish to ZooKeeper.
 */
public class PublishingEndpointListenerFactory implements ServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PublishingEndpointListenerFactory.class);

    private final BundleContext bctx;
    private final ZooKeeper zk;
    private final List<PublishingEndpointListener> listeners = new ArrayList<PublishingEndpointListener>();
    private ServiceRegistration serviceRegistration;

    public PublishingEndpointListenerFactory(ZooKeeper zk, BundleContext bctx) {
        this.bctx = bctx;
        this.zk = zk;
    }

    public PublishingEndpointListener getService(Bundle b, ServiceRegistration sr) {
        LOG.debug("new EndpointListener from factory");
        synchronized (listeners) {
            PublishingEndpointListener pel = new PublishingEndpointListener(zk, bctx);
            listeners.add(pel);
            return pel;
        }
    }

    public void ungetService(Bundle b, ServiceRegistration sr,
                             Object pel) {
        LOG.debug("remove EndpointListener");
        synchronized (listeners) {
            if (listeners.remove(pel)) {
                if (pel instanceof PublishingEndpointListener)
                {
                    PublishingEndpointListener listener = (PublishingEndpointListener)pel;
                    listener.close();

                }
            }
        }
    }

    public synchronized void start() {
        Dictionary<String, String> props = new Hashtable<String, String>();
        String uuid = bctx.getProperty("org.osgi.framework.uuid");
        if(uuid==null)
        {
            uuid = System.getProperty("org.osgi.framework.uuid",UUID.randomUUID().toString());
            System.setProperty("org.osgi.framework.uuid", uuid);
        }
        props.put(EndpointListener.ENDPOINT_LISTENER_SCOPE,
                  String.format("(&(%s=*)(%s=%s))", Constants.OBJECTCLASS,
                                RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid));
        props.put(ZooKeeperDiscovery.DISCOVERY_ZOOKEEPER_ID, "true");
        serviceRegistration = bctx.registerService(EndpointListener.class.getName(), this, props);
    }

    public synchronized void stop() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
        synchronized (listeners) {
            for (PublishingEndpointListener pel : listeners) {
                pel.close();
            }
            listeners.clear();
        }
    }

    /**
     * Only for the test case!
     *
     * @return
     */
    protected List<PublishingEndpointListener> getListeners() {
        synchronized (listeners) {
            return listeners;
        }
    }
}
