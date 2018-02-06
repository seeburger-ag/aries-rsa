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

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Tracks interest in EndpointListeners. Delegates to InterfaceMonitorManager to manage
 * interest in the scopes of each EndpointListener.
 */
@SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
public class EndpointListenerTracker extends ServiceTracker {
    private final InterfaceMonitorManager imManager;

    public EndpointListenerTracker(BundleContext bctx, InterfaceMonitorManager imManager) {
        super(bctx, getfilter(), null);
        this.imManager = imManager;
    }
    
    private static Filter getfilter() {
        String filterSt = String.format("(|(objectClass=%s)(objectClass=%s))", EndpointEventListener.class.getName(), 
                EndpointListener.class.getName());
        try {
            return FrameworkUtil.createFilter(filterSt);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public Object addingService(ServiceReference endpointListener) {
        imManager.addInterest(endpointListener);
        return null;
    }

    @Override
    public void modifiedService(ServiceReference endpointListener, Object service) {
        // called when an EndpointListener updates its service properties,
        // e.g. when its interest scope is expanded/reduced
        imManager.addInterest(endpointListener);
    }

    @Override
    public void removedService(ServiceReference endpointListener, Object service) {
        imManager.removeInterest(endpointListener);
    }

}
