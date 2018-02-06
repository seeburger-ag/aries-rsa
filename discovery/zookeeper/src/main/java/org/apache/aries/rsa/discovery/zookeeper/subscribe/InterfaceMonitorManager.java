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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.aries.rsa.discovery.zookeeper.ZooKeeperDiscovery;
import org.apache.aries.rsa.discovery.zookeeper.util.Utils;
import org.apache.aries.rsa.util.StringPlus;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the EndpointEventListeners and the scopes they are interested in.
 * For each scope with interested EndpointEventListeners an InterfaceMonitor is created.
 * The InterfaceMonitor calls back when it detects added or removed external Endpoints.
 * These events are then forwarded to all interested EndpointEventListeners.
 */
@SuppressWarnings({"deprecation", "rawtypes"})
public class InterfaceMonitorManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceMonitorManager.class);
    private static final Pattern OBJECTCLASS_PATTERN = Pattern.compile(".*\\(objectClass=([^)]+)\\).*");

    private final BundleContext bctx;
    private final ZooKeeper zk;
    // map of EndpointEventListeners and the scopes they are interested in
    private final Map<ServiceReference, List<String>> epListenerScopes =
            new HashMap<ServiceReference, List<String>>();
    // map of scopes and their interest data
    private final Map<String, Interest> interests = new HashMap<String, Interest>();

    protected static class Interest {
        List<ServiceReference> epListeners = 
            new CopyOnWriteArrayList<ServiceReference>();
        InterfaceMonitor monitor;
    }

    public InterfaceMonitorManager(BundleContext bctx, ZooKeeper zk) {
        this.bctx = bctx;
        this.zk = zk;
    }

    public void addInterest(ServiceReference<?> eplistener) {
        if (isOurOwnEndpointEventListener(eplistener)) {
            LOG.debug("Skipping our own EndpointEventListener");
            return;
        }
        List<String> scopes = getScopes(eplistener);
        LOG.debug("adding Interests: {}", scopes);
        
        for (String scope : scopes) {
            String objClass = getObjectClass(scope);
            addInterest(eplistener, scope, objClass);
        }
    }

    private static boolean isOurOwnEndpointEventListener(ServiceReference<?> EndpointEventListener) {
        return Boolean.parseBoolean(String.valueOf(
                EndpointEventListener.getProperty(ZooKeeperDiscovery.DISCOVERY_ZOOKEEPER_ID)));
    }

    public synchronized void addInterest(ServiceReference epListener, 
                                         String scope, String objClass) {
        // get or create interest for given scope and add listener to it
        Interest interest = interests.get(scope);
        if (interest == null) {
            // create interest, add listener and start monitor
            interest = new Interest();
            interests.put(scope, interest);
            interest.epListeners.add(epListener); // add it before monitor starts so we don't miss events
            interest.monitor = createInterfaceMonitor(scope, objClass, interest);
            interest.monitor.start();
        } else {
            // interest already exists, so just add listener to it
            if (!interest.epListeners.contains(epListener)) {
                interest.epListeners.add(epListener);
            }
            // notify listener of all known endpoints for given scope
            // (as EndpointEventListener contract requires of all added/modified listeners)
            for (EndpointDescription endpoint : interest.monitor.getEndpoints()) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                notifyListeners(event, scope, Arrays.asList(epListener));
            }
        }

        // add scope to listener's scopes list
        List<String> scopes = epListenerScopes.get(epListener);
        if (scopes == null) {
            scopes = new ArrayList<String>(1);
            epListenerScopes.put(epListener, scopes);
        }
        if (!scopes.contains(scope)) {
            scopes.add(scope);
        }
    }

    public synchronized void removeInterest(ServiceReference<EndpointEventListener> EndpointEventListener) {
        LOG.info("removing EndpointEventListener interests: {}", EndpointEventListener);
        List<String> scopes = epListenerScopes.get(EndpointEventListener);
        if (scopes == null) {
            return;
        }

        for (String scope : scopes) {
            Interest interest = interests.get(scope);
            if (interest != null) {
                interest.epListeners.remove(EndpointEventListener);
                if (interest.epListeners.isEmpty()) {
                    interest.monitor.close();
                    interests.remove(scope);
                }
            }
        }
        epListenerScopes.remove(EndpointEventListener);
    }

    protected InterfaceMonitor createInterfaceMonitor(final String scope, String objClass, final Interest interest) {
        // holding this object's lock in the callbacks can lead to a deadlock with InterfaceMonitor
        EndpointEventListener listener = new EndpointEventListener() {

            @Override
            public void endpointChanged(EndpointEvent event, String filter) {
                notifyListeners(event, scope, interest.epListeners);
            }
        };
        return new InterfaceMonitor(zk, objClass, listener, scope);
    }

    private void notifyListeners(EndpointEvent event, String currentScope,
            List<ServiceReference> epListeners) {
        EndpointDescription endpoint = event.getEndpoint();
        for (ServiceReference<?> epListenerRef : epListeners) {
            if (epListenerRef.getBundle() == null) {
                LOG.info("listening service was unregistered, ignoring");
            }
            Object service = bctx.getService(epListenerRef);
            LOG.trace("matching {} against {}", endpoint, currentScope);
            if (matchFilter(bctx, currentScope, endpoint)) {
                LOG.debug("Matched {} against {}", endpoint, currentScope);
            try {
                if (service instanceof EndpointEventListener) {
                    EndpointEventListener epeListener = (EndpointEventListener)service;
                    notifyListener(event, currentScope, epeListener);
                } else if (service instanceof EndpointListener) {
                    EndpointListener epListener = (EndpointListener)service;
                    notifyListener(event, currentScope, epListener);
                }
            } finally {
                if (service != null) {
                    bctx.ungetService(epListenerRef);
                }
            }
            }
        }
    }
    
    private static boolean matchFilter(BundleContext bctx, String filter, EndpointDescription endpoint) {
        if (filter == null) {
            return false;
        }
    
        try {
            Filter f = bctx.createFilter(filter);
            Dictionary<String, Object> dict = new Hashtable<String, Object>(endpoint.getProperties());
            return f.match(dict);
        } catch (Exception e) {
            return false;
        }
    }


    private void notifyListener(EndpointEvent event, String currentScope, EndpointEventListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling endpointchanged on class {} for filter {}, type {}, endpoint {} ", listener, currentScope, endpoint);
        listener.endpointChanged(event, currentScope);
    }
    
    private void notifyListener(EndpointEvent event, String currentScope, EndpointListener listener) {
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
        for (Interest interest : interests.values()) {
            interest.monitor.close();
        }
        interests.clear();
        epListenerScopes.clear();
    }

    /**
     * Only for test case!
     */
    protected synchronized Map<String, Interest> getInterests() {
        return interests;
    }

    /**
     * Only for test case!
     */
    protected synchronized Map<ServiceReference, List<String>> getEndpointListenerScopes() {
        return epListenerScopes;
    }

    protected List<String> getScopes(ServiceReference<?> sref) {
        return Utils.removeEmpty(StringPlus.normalize(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE)));
    }
    
    public static String getObjectClass(String scope) {
        Matcher m = OBJECTCLASS_PATTERN.matcher(scope);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Returns a service's properties as a map.
     *
     * @param serviceReference a service reference
     * @return the service's properties as a map
     */
    public static Map<String, Object> getProperties(ServiceReference<?> serviceReference) {
        String[] keys = serviceReference.getPropertyKeys();
        Map<String, Object> props = new HashMap<String, Object>(keys.length);
        for (String key : keys) {
            Object val = serviceReference.getProperty(key);
            props.put(key, val);
        }
        return props;
    }
}
