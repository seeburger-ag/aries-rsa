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
package org.apache.aries.rsa.discovery.zookeeper.subscribe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.discovery.zookeeper.ZooKeeperDiscovery;
import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the EndpointEventListeners and the scopes they are interested in.
 * Establishes a listener with the repository to be called back on all changes in the repo.
 * Events from repository are then forwarded to all interested EndpointEventListeners.
 */
@SuppressWarnings({"deprecation", "rawtypes"})
public class InterestManager implements EndpointEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);

    private final ZookeeperEndpointRepository repository;
    private final Map<ServiceReference, Interest> interests = new HashMap<ServiceReference, Interest>();

    protected static class Interest {
        List<String> scopes;
        Object epListener;
    }

    public InterestManager(ZookeeperEndpointRepository repository) {
        this.repository = repository;
    }

    public void addInterest(ServiceReference<?> sref, Object epListener) {
        if (isOurOwnEndpointEventListener(sref)) {
            LOG.debug("Skipping our own EndpointEventListener");
            return;
        }
        List<String> scopes = getScopes(sref);
        LOG.debug("adding Interests: {}", scopes);
        
        // get or create interest for given scope and add listener to it
        Interest interest = interests.get(epListener);
        if (interest == null) {
            // create interest, add listener and start monitor
            interest = new Interest();
            interest.epListener = epListener;
            interest.scopes = scopes;
            interests.put(sref, interest);
            sendExistingEndpoints(scopes, epListener);
        }
    }

    private void sendExistingEndpoints(List<String> scopes, Object epListener) {
        for (EndpointDescription endpoint : repository.getAll()) {
            EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
            notifyListener(event, scopes, epListener);
        }
    }

    private static boolean isOurOwnEndpointEventListener(ServiceReference<?> EndpointEventListener) {
        return Boolean.parseBoolean(String.valueOf(
                EndpointEventListener.getProperty(ZooKeeperDiscovery.DISCOVERY_ZOOKEEPER_ID)));
    }

    public synchronized void removeInterest(ServiceReference<EndpointEventListener> epListenerRef) {
        LOG.info("removing EndpointEventListener interests: {}", epListenerRef);
        interests.remove(epListenerRef);
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        for (Interest interest : interests.values()) {
            notifyListener(event, interest.scopes, interest.epListener);
        }
    }

    private void notifyListener(EndpointEvent event, List<String> scopes, Object service) {
        EndpointDescription endpoint = event.getEndpoint();
        String currentScope = getFirstMatch(scopes, endpoint);
        if (currentScope == null) {
            return;
        }
        LOG.debug("Matched {} against {}", endpoint, currentScope);
        if (service instanceof EndpointEventListener) {
            notifyEEListener(event, currentScope, (EndpointEventListener)service);
        } else if (service instanceof EndpointListener) {
            notifyEListener(event, currentScope, (EndpointListener)service);
        }
    }
    
    private String getFirstMatch(List<String> scopes, EndpointDescription endpoint) {
        for (String scope : scopes) {
            if (endpoint.matches(scope)) {
                return scope;
            }
        }
        return null;
    }

    private void notifyEEListener(EndpointEvent event, String currentScope, EndpointEventListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling endpointchanged on class {} for filter {}, type {}, endpoint {} ", listener, currentScope, endpoint);
        listener.endpointChanged(event, currentScope);
    }
    
    private void notifyEListener(EndpointEvent event, String currentScope, EndpointListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling old listener on class {} for filter {}, type {}, endpoint {} ", listener, currentScope, endpoint);
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            listener.endpointAdded(endpoint, currentScope);
            break;

        case EndpointEvent.MODIFIED:
            listener.endpointAdded(endpoint, currentScope);
            listener.endpointRemoved(endpoint, currentScope);
            break;

        case EndpointEvent.REMOVED:
            listener.endpointRemoved(endpoint, currentScope);
            break;
        }
    }

    public synchronized void close() {
        interests.clear();
    }

    /**
     * Only for test case!
     */
    protected synchronized Map<ServiceReference, Interest> getInterests() {
        return interests;
    }

    protected List<String> getScopes(ServiceReference<?> sref) {
        return StringPlus.normalize(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
    }

}
