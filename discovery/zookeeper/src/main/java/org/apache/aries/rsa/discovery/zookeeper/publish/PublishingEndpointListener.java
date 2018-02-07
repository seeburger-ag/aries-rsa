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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.discovery.zookeeper.ZooKeeperDiscovery;
import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for local EndpointEvents using old and new style listeners and publishes changes to 
 * the ZooKeeperEndpointRepository
 */
@SuppressWarnings("deprecation")
public class PublishingEndpointListener implements EndpointEventListener, EndpointListener {

    private static final Logger LOG = LoggerFactory.getLogger(PublishingEndpointListener.class);

    private ServiceRegistration<?> listenerReg;
    private ZookeeperEndpointRepository repository;

    public PublishingEndpointListener(ZookeeperEndpointRepository repository) {
        this.repository = repository;
    }
    
    public void start(BundleContext bctx) {
        Dictionary<String, String> props = new Hashtable<String, String>();
        String uuid = bctx.getProperty(Constants.FRAMEWORK_UUID);
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, 
                  String.format("(&(%s=*)(%s=%s))", Constants.OBJECTCLASS, 
                                RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid));
        props.put(ZooKeeperDiscovery.DISCOVERY_ZOOKEEPER_ID, "true");
        String[] ifAr = {EndpointEventListener.class.getName(), EndpointListener.class.getName()};
        listenerReg = bctx.registerService(ifAr, this, props);
    }
    
    public void stop() {
        if (listenerReg != null) {
            listenerReg.unregister();
            listenerReg = null;
        }
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        EndpointDescription endpoint = event.getEndpoint();
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            endpointAdded(endpoint, filter);
            break;
        case EndpointEvent.REMOVED:
            endpointRemoved(endpoint, filter);
            break;
        case EndpointEvent.MODIFIED:
            endpointModified(endpoint, filter);
            break;
        }
    }
    
    private void endpointModified(EndpointDescription endpoint, String filter) {
        try {
            repository.modify(endpoint);
        } catch (Exception e) {
            LOG.error("Error modifying endpoint data in zookeeper for endpoint {}", endpoint.getId(), e);
        }
    }

    @Override
    public void endpointAdded(EndpointDescription endpoint, String matchedFilter) {
        try {
            repository.add(endpoint);
        } catch (Exception ex) {
            LOG.error("Exception while processing the addition of an endpoint.", ex);
        }
    }

    @Override
    public void endpointRemoved(EndpointDescription endpoint, String matchedFilter) {
        try {
            repository.remove(endpoint);
        } catch (Exception ex) {
            LOG.error("Exception while processing the removal of an endpoint", ex);
        }
    }

}
