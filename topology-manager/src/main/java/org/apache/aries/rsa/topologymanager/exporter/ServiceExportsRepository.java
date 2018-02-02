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
public class ServiceExportsRepository  implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceExportsRepository.class);

    private RemoteServiceAdmin rsa;
    private EndpointListenerNotifier notifier;

    private final Map<ServiceReference<?>, Collection<ExportRegistration>> exportsMap = new LinkedHashMap<>();


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

    private void closeReg(ExportRegistration reg) {
        ExportReference exportReference = reg.getExportReference();
        if (exportReference != null) {
            EndpointDescription endpoint = exportReference.getExportedEndpoint();
            notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
            reg.close();
        }
    }
    
    public synchronized void addService(ServiceReference<?> sref, Collection<ExportRegistration> exports) {
        exportsMap.put(sref, new ArrayList<ExportRegistration>(exports));
        for (ExportRegistration reg : exports) {
            ExportReference exportReference = reg.getExportReference();
            if  (exportReference != null) {
                EndpointDescription endpoint = exportReference.getExportedEndpoint();
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                notifier.sendEvent(event);
            }
        }
    }
    

    public synchronized void modifyService(ServiceReference<?> sref) {
        Collection<ExportRegistration> exports = exportsMap.get(sref);
        if (exports != null) {
            for (ExportRegistration reg : exports) {
                reg.update(null);
                ExportReference exportReference = reg.getExportReference();
                if  (exportReference != null) {
                    EndpointDescription endpoint = exportReference.getExportedEndpoint();
                    EndpointEvent event = new EndpointEvent(EndpointEvent.MODIFIED, endpoint);
                    notifier.sendEvent(event);
                }
            }
        }
    }

    public synchronized void removeService(ServiceReference<?> sref) {
        Collection<ExportRegistration> exports = exportsMap.get(sref);
        if (exports != null) {
            for (ExportRegistration reg : exports) {
                closeReg(reg);
            }
            exports.clear();
        }
    }
    
    public List<EndpointDescription> getAllEndpoints() {
        List<EndpointDescription> endpoints = new ArrayList<>();
        for (ServiceReference<?> sref : exportsMap.keySet()) {
            Collection<ExportRegistration> exports = exportsMap.get(sref);
            for (ExportRegistration reg : exports) {
                ExportReference exportRef = reg.getExportReference();
                if (exportRef != null) {
                    endpoints.add(exportRef.getExportedEndpoint());
                }
            }
        }
        return endpoints;
    }
}
