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
package org.apache.aries.rsa.topologymanager.exporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.aries.rsa.spi.ExportPolicy;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Bundle;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages exported endpoints of DOSGi services and notifies EndpointListeners of changes.
 * <ul>
 * <li> Tracks local RemoteServiceAdmin instances by using a ServiceTracker
 * <li> Uses a ServiceListener to track local OSGi services
 * <li> When a service is published that is supported by DOSGi the
 *      known RemoteServiceAdmins are instructed to export the service and
 *      the EndpointListeners are notified
 * <li> When a service is unpublished the EndpointListeners are notified.
 *      The endpoints are not closed as the ExportRegistration takes care of this
 * </ul>
 */
public class TopologyManagerExport implements ServiceListener {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerExport.class);

    private final EndpointListenerNotifier notifier;
    private final Executor executor;
    private final ExportPolicy policy;
    private final Map<RemoteServiceAdmin, ServiceExportsRepository> endpointRepo;
    private final Set<ServiceReference<?>> toBeExported;

    public TopologyManagerExport(EndpointListenerNotifier notifier, Executor executor, ExportPolicy policy) {
        this.notifier = notifier;
        this.executor = executor;
        this.policy = policy;
        this.endpointRepo = new ConcurrentHashMap<>();
        this.toBeExported = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private String getTypeName(ServiceEvent event) {
        switch (event.getType()) {
            case ServiceEvent.MODIFIED: return "modified";
            case ServiceEvent.MODIFIED_ENDMATCH: return "modified endmatch";
            case ServiceEvent.REGISTERED: return "registered";
            case ServiceEvent.UNREGISTERING: return "unregistering";
            default: return null;
        }
    }

    // track all service registrations, so we can export any services that are configured to be exported
    // ServiceListener events may be delivered out of order, concurrently, re-entrant, etc. (see spec or docs)
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> sref = event.getServiceReference();
        if (!shouldExport(sref)) {
            LOG.debug("Skipping service {}", sref);
            return;
        }
        LOG.info("Received ServiceEvent type: {}, sref: {}", getTypeName(event), sref);
        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                doExport(sref);
                break;

            case ServiceEvent.MODIFIED:
                modified(sref);
                break;

            case ServiceEvent.UNREGISTERING:
            case ServiceEvent.MODIFIED_ENDMATCH:
                remove(sref);
                break;
        }
    }

    private void modified(ServiceReference<?> sref) {
        for (ServiceExportsRepository repo : endpointRepo.values()) {
            repo.modifyService(sref);
        }
    }

    private void remove(ServiceReference<?> sref) {
        toBeExported.remove(sref);
        for (ServiceExportsRepository repo : endpointRepo.values()) {
            repo.removeService(sref);
        }
    }

    public void add(RemoteServiceAdmin rsa) {
        endpointRepo.put(rsa, new ServiceExportsRepository(rsa, notifier));
        for (ServiceReference<?> sref : toBeExported) {
            exportInBackground(sref);
        }
    }

    public void remove(RemoteServiceAdmin rsa) {
        ServiceExportsRepository repo = endpointRepo.remove(rsa);
        if (repo != null) {
            repo.close();
        }
    }

    private void exportInBackground(final ServiceReference<?> sref) {
        executor.execute(() -> doExport(sref));
    }

    private void doExport(final ServiceReference<?> sref) {
        LOG.debug("Exporting service {}", sref);
        toBeExported.add(sref);
        if (endpointRepo.isEmpty()) {
            Bundle bundle = sref.getBundle();
            String bundleName = bundle == null ? null : bundle.getSymbolicName();
            LOG.error("Unable to export service from bundle {}, interfaces: {} as no RemoteServiceAdmin is available. Marked for later export.",
                      bundleName, sref.getProperty(org.osgi.framework.Constants.OBJECTCLASS));
            return;
        }

        for (Map.Entry<RemoteServiceAdmin, ServiceExportsRepository> entry : endpointRepo.entrySet()) {
            Collection<ExportRegistration> regs = exportService(entry.getKey(), sref);
            entry.getValue().addService(sref, regs);
        }
    }

    private Collection<ExportRegistration> exportService(final RemoteServiceAdmin rsa, final ServiceReference<?> sref) {
        // abort if the service was unregistered by the time we got here
        // (we check again at the end, but this optimization saves unnecessary heavy processing)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export aborted for {} since it was unregistered", sref);
            return Collections.emptyList();
        }

        LOG.debug("exporting Service {} using RemoteServiceAdmin {}", sref, rsa.getClass().getName());
        Map<String, ?> addProps = policy.additionalParameters(sref);
        Collection<ExportRegistration> regs = rsa.exportService(sref, addProps);

        // process successful/failed registrations
        for (ExportRegistration reg : regs) {
            if (reg.getException() == null) {
                EndpointDescription endpoint = reg.getExportReference().getExportedEndpoint();
                LOG.info("TopologyManager: export succeeded for {}, endpoint {}, rsa {}", sref, endpoint, rsa.getClass().getName());
            } else {
                LOG.error("TopologyManager: export failed for {}", sref, reg.getException());
                reg.close();
            }
        }

        // abort export if service was unregistered in the meanwhile (since we have a race
        // with the unregister event which may have already been handled, so we'll miss it)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export reverted for {} since service was unregistered", sref);
            for (ExportRegistration reg : regs) {
                reg.close();
            }
        }

        return regs;
    }

    private boolean shouldExport(ServiceReference<?> sref) {
        Map<String, ?> addProps = policy.additionalParameters(sref);
        List<String> exported = StringPlus.normalize(sref.getProperty(RemoteConstants.SERVICE_EXPORTED_INTERFACES));
        List<String> addExported = StringPlus.normalize(addProps.get(RemoteConstants.SERVICE_EXPORTED_INTERFACES));
        return exported != null && !exported.isEmpty() || addExported != null && !addExported.isEmpty();
    }

    public void addEPListener(EndpointEventListener listener, Set<Filter> filters) {
        Collection<EndpointDescription> endpoints = new ArrayList<>();
        for (ServiceExportsRepository repo : endpointRepo.values()) {
            endpoints.addAll(repo.getAllEndpoints());
        }
        notifier.add(listener, filters, endpoints);
    }

    public void removeEPListener(EndpointEventListener listener) {
        notifier.remove(listener);
    }
}
