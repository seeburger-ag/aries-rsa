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
package org.apache.aries.rsa.topologymanager.exporter;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all Exports of a given RemoteServiceAdmin
 */
public class ServiceExportsRepository implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceExportsRepository.class);

    private RemoteServiceAdmin rsa;
    private EndpointListenerNotifier notifier;

    private final Map<ServiceReference<?>, Collection<ExportRegistrationHolder>> exportsMap = new LinkedHashMap<>();

    /**
     * The holder allows us to work around that registration.getReference() is null when the registration is closed
     */
    private class ExportRegistrationHolder {
        private final ExportRegistration registration;
        private final ExportReference reference;
        private final EndpointDescription endpoint;

        ExportRegistrationHolder(ExportRegistration registration) {
            this.registration = registration;
            this.reference = registration.getExportReference();
            this.endpoint = this.reference.getExportedEndpoint();
        }

        ExportRegistration getRegistration() {
            return registration;
        }

        void close() {
            if (reference != null) {
                notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
                registration.close();
            }
        }

        public void update() {
            registration.update(null);
            if (reference != null) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.MODIFIED, endpoint);
                notifier.sendEvent(event);
            }
        }
    }

    public ServiceExportsRepository(RemoteServiceAdmin rsa, EndpointListenerNotifier notifier) {
        this.rsa = rsa;
        this.notifier = notifier;
    }

    public void close() {
        LOG.debug("Closing registry for RemoteServiceAdmin {}", rsa.getClass().getName());
        for (ServiceReference<?> sref : exportsMap.keySet()) {
            removeService(sref);
        }
    }

    public synchronized void addService(ServiceReference<?> sref, Collection<ExportRegistration> exports) {
        List<ExportRegistrationHolder> holderList = new ArrayList<ExportRegistrationHolder>(exports.size());
        exportsMap.put(sref, holderList);
        for (ExportRegistration reg : exports) {
            ExportRegistrationHolder holder = new ExportRegistrationHolder(reg);
            holderList.add(holder);
            ExportReference exportReference = reg.getExportReference();
            if (exportReference != null) {
                EndpointDescription endpoint = exportReference.getExportedEndpoint();
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                notifier.sendEvent(event);
            }
        }
    }

    public synchronized void modifyService(ServiceReference<?> sref) {
        Collection<ExportRegistrationHolder> exports = exportsMap.get(sref);
        if (exports != null) {
            for (ExportRegistrationHolder reg : exports) {
                reg.update();
            }
        }
    }

    public synchronized void removeService(ServiceReference<?> sref) {
        Collection<ExportRegistrationHolder> exports = exportsMap.get(sref);
        if (exports != null) {
            for (ExportRegistrationHolder reg : exports) {
                reg.close();
            }
            exports.clear();
        }
    }

    public List<EndpointDescription> getAllEndpoints() {
        List<EndpointDescription> endpoints = new ArrayList<>();
        for (ServiceReference<?> sref : exportsMap.keySet()) {
            Collection<ExportRegistrationHolder> exports = exportsMap.get(sref);
            for (ExportRegistrationHolder reg : exports) {
                ExportReference exportRef = reg.getRegistration().getExportReference();
                if (exportRef != null) {
                    endpoints.add(exportRef.getExportedEndpoint());
                }
            }
        }
        return endpoints;
    }
}
