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
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages an EndpointListener and adjusts its scope according to requested service filters.
 */
public class EndpointListenerManager {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointListenerManager.class);

    private final BundleContext bctx;
    private volatile ServiceRegistration serviceRegistration;
    private final List<String> filters = new ArrayList<String>();
    private final EndpointListener endpointListener;

    public EndpointListenerManager(BundleContext bc, EndpointListener endpointListener) {
        this.bctx = bc;
        this.endpointListener = endpointListener;
    }

    protected void start() {
        serviceRegistration = bctx.registerService(EndpointListener.class.getName(), endpointListener,
                                                   getRegistrationProperties());
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
}
