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
package org.apache.aries.rsa.discovery.local;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {
    private ServiceTracker<EndpointEventListener, EndpointEventListener> listenerTracker;
    private LocalDiscovery localDiscovery;

    public void start(BundleContext context) {
        localDiscovery = new LocalDiscovery();
        listenerTracker = new EPListenerTracker(context, localDiscovery);
        listenerTracker.open();
        localDiscovery.processExistingBundles(context.getBundles());
        context.addBundleListener(localDiscovery);
    }

    public void stop(BundleContext context) {
        listenerTracker.close();
        context.removeBundleListener(localDiscovery);
    }

    private final class EPListenerTracker extends ServiceTracker<EndpointEventListener, EndpointEventListener> {
        private final LocalDiscovery localDiscovery;
    
        private EPListenerTracker(BundleContext context, LocalDiscovery localDiscovery) {
            super(context, EndpointEventListener.class, null);
            this.localDiscovery = localDiscovery;
        }
    
        @Override
        public EndpointEventListener addingService(ServiceReference<EndpointEventListener> reference) {
            EndpointEventListener service = super.addingService(reference);
            localDiscovery.addListener(reference, service);
            return service;
        }
    
        @Override
        public void modifiedService(ServiceReference<EndpointEventListener> reference, EndpointEventListener service) {
            super.modifiedService(reference, service);
            localDiscovery.removeListener(service);
    
            // This may cause duplicate registrations of remote services,
            // but that's fine and should be filtered out on another level.
            // See Remote Service Admin spec section 122.6.3
            localDiscovery.addListener(reference, service);
        }
    
        @Override
        public void removedService(ServiceReference<EndpointEventListener> reference, EndpointEventListener service) {
            super.removedService(reference, service);
            localDiscovery.removeListener(service);
        }
    }

    
}
