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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the endpoint listener for the import of external services.
 * The endpoint listener scope reflects the combined filters of all services 
 * that are asked for (by listeners and service lookups) in the current system. 
 * 
 * Discovery will then send callbacks when external endpoints are added / removed that match
 * the interest in the local system.
 */
public class EndpointListenerManager implements ServiceInterestListener{

    private static final Logger LOG = LoggerFactory.getLogger(EndpointListenerManager.class);

    private final BundleContext bctx;
    private volatile ServiceRegistration<EndpointListener> serviceRegistration;
    private final List<String> filters = new ArrayList<String>();
    private final EndpointListener endpointListener;
    private final ListenerHookImpl listenerHook;
    private RSFindHook findHook;
    
    /**
     * Count service interest by filter. This allows to modify the scope of the EndpointListener as seldom as possible
     */
    private final ReferenceCounter<String> importInterestsCounter = new ReferenceCounter<String>();

    public EndpointListenerManager(BundleContext bc, EndpointListener endpointListener) {
        this.bctx = bc;
        this.endpointListener = endpointListener;
        this.listenerHook = new ListenerHookImpl(bc, this);
        findHook = new RSFindHook(bc, this);
    }

    public void start() {
        serviceRegistration = bctx.registerService(EndpointListener.class, endpointListener,
                                                   getRegistrationProperties());
        bctx.registerService(ListenerHook.class, listenerHook, null);
        bctx.registerService(FindHook.class, findHook, null);
    }

    public void stop() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    protected void extendScope(String filter) {
        if (filter == null) {
            return;
        }
        LOG.debug("EndpointListener: extending scope by {}", filter);
        synchronized (filters) {
            filters.add(filter);
        }
        updateRegistration();
    }

    protected void reduceScope(String filter) {
        if (filter == null) {
            return;
        }
        LOG.debug("EndpointListener: reducing scope by {}", filter);
        synchronized (filters) {
            filters.remove(filter);
        }
        updateRegistration();
    }

    private Dictionary<String, Object> getRegistrationProperties() {
        Dictionary<String, Object> p = new Hashtable<String, Object>();

        synchronized (filters) {
            LOG.debug("Current filter: {}", filters);
            p.put(EndpointListener.ENDPOINT_LISTENER_SCOPE, new ArrayList<String>(filters));
        }

        return p;
    }

    private void updateRegistration() {
        if (serviceRegistration != null) {
            serviceRegistration.setProperties(getRegistrationProperties());
        }
    }

    @Override
    public void addServiceInterest(String filter) {
        if (importInterestsCounter.add(filter) == 1) {
            extendScope(filter);
        }
    }

    @Override
    public void removeServiceInterest(String filter) {
        if (importInterestsCounter.remove(filter) == 0) {
            LOG.debug("last reference to import interest is gone -> removing interest filter: {}", filter);
            reduceScope(filter);
        }
    }
}
