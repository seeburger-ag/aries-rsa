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
package org.apache.aries.rsa.topologymanager;

import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerNotifier;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings({ "deprecation", "rawtypes", "unchecked" })
final class EndpointEventListenerTracker extends ServiceTracker<EndpointEventListener, EndpointEventListener> {
    private TopologyManagerExport tmExport;

    EndpointEventListenerTracker(BundleContext context, TopologyManagerExport tmExport) {
        super(context, getFilter(), null);
        this.tmExport = tmExport;
    }

    private static Filter getFilter() {
        String filterSt = String.format("(objectClass=%s)", EndpointEventListener.class.getName());
        try {
            return FrameworkUtil.createFilter(filterSt);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public EndpointEventListener addingService(ServiceReference reference) {
        EndpointEventListener listener = super.addingService(reference);
        this.tmExport.addEPListener(listener, EndpointListenerNotifier.filtersFromEEL(reference));
        return listener;
    }

    @Override
    public void modifiedService(ServiceReference reference, EndpointEventListener listener) {
        this.tmExport.addEPListener(listener, EndpointListenerNotifier.filtersFromEEL(reference));
        super.modifiedService(reference, listener);
    }

    @Override
    public void removedService(ServiceReference reference, EndpointEventListener listener) {
        this.tmExport.removeEPListener(listener);
        super.removedService(reference, listener);
    }

}
