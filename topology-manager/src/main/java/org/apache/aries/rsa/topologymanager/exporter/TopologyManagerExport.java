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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.aries.rsa.spi.ExportPolicy;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
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

    private final Executor execService;
    private final EndpointRepository endpointRepo;
    private ExportPolicy policy;
    private final Set<RemoteServiceAdmin> rsaSet;


    public TopologyManagerExport(final EndpointRepository endpointRepo, Executor executor, ExportPolicy policy) {
        this.endpointRepo = endpointRepo;
        this.policy = policy;
        this.rsaSet = new HashSet<RemoteServiceAdmin>();
        this.execService = executor;
    }

    // track all service registrations so we can export any services that are configured to be exported
    // ServiceListener events may be delivered out of order, concurrently, re-entrant, etc. (see spec or docs)
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> sref = event.getServiceReference();
        if (event.getType() == ServiceEvent.REGISTERED) {
            LOG.debug("Received REGISTERED ServiceEvent: {}", event);
            export(sref);
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            LOG.debug("Received UNREGISTERING ServiceEvent: {}", event);
            endpointRepo.removeService(sref);
        }
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        for (ServiceReference<?> serviceRef : endpointRepo.getServicesToBeExportedFor(rsa)) {
            export(serviceRef);
        }
    };

    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
        endpointRepo.removeRemoteServiceAdmin(rsa);
    };

    private void export(final ServiceReference<?> sref) {
        execService.execute(new Runnable() {
            public void run() {
                doExport(sref);
            }
        });
    }

    private void doExport(final ServiceReference<?> sref) {
        Map<String, ?> addProps = policy.additionalParameters(sref);
        if (!shouldExport(sref, addProps)) {
            LOG.debug("Skipping service {}", sref);
            return;
        }
        LOG.debug("Exporting service {}", sref);
        endpointRepo.addService(sref); // mark for future export even if there are currently no RSAs
        if (rsaSet.size() == 0) {
            LOG.error("No RemoteServiceAdmin available! Unable to export service from bundle {}, interfaces: {}",
                    getSymbolicName(sref.getBundle()),
                    sref.getProperty(org.osgi.framework.Constants.OBJECTCLASS));
            return;
        }

        HashSet<RemoteServiceAdmin> rsaSetCopy = new HashSet<>(rsaSet);
        for (RemoteServiceAdmin remoteServiceAdmin : rsaSetCopy) {
            LOG.debug("TopologyManager: handling remoteServiceAdmin " + remoteServiceAdmin);
            if (endpointRepo.isAlreadyExportedForRsa(sref, remoteServiceAdmin)) {
                // already handled by this remoteServiceAdmin
                LOG.debug("already handled by this remoteServiceAdmin -> skipping");
            } else {

                exportServiceUsingRemoteServiceAdmin(sref, remoteServiceAdmin, addProps);
            }
        }
    }

    private boolean shouldExport(ServiceReference<?> sref, Map<String, ?> addProps) {
        List<String> exported= StringPlus.normalize(sref.getProperty(RemoteConstants.SERVICE_EXPORTED_INTERFACES));
        List<String> addExported = StringPlus.normalize(addProps.get(RemoteConstants.SERVICE_EXPORTED_INTERFACES));
        int length = exported == null ? 0 : exported.size();
        length += addExported == null ? 0 : addExported.size();
        return length>0;
    }

    private Object getSymbolicName(Bundle bundle) {
        return bundle == null ? null : bundle.getSymbolicName();
    }

    private void exportServiceUsingRemoteServiceAdmin(final ServiceReference<?> sref,
                                                      final RemoteServiceAdmin remoteServiceAdmin,
                                                      Map<String, ?> addProps) {
        // abort if the service was unregistered by the time we got here
        // (we check again at the end, but this optimization saves unnecessary heavy processing)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export aborted for {} since it was unregistered", sref);
            endpointRepo.removeService(sref);
            return;
        }
        // do the export
        LOG.debug("exporting {}...", sref);
        // TODO: additional parameter Map?
        Collection<ExportRegistration> exportRegs = remoteServiceAdmin.exportService(sref, addProps);
        // process successful/failed registrations
        List<EndpointDescription> endpoints = new ArrayList<EndpointDescription>();
        for (ExportRegistration reg : exportRegs) {
            if (reg.getException() == null) {
                EndpointDescription endpoint = getExportedEndpoint(reg);
                LOG.debug("TopologyManager: export succeeded for {}, endpoint {}, rsa {}", sref, endpoint, remoteServiceAdmin.getClass());
                endpoints.add(endpoint);
            } else {
                LOG.error("TopologyManager: export failed for {}", sref, reg.getException());
                reg.close();
            }
        }
        // abort export if service was unregistered in the meanwhile (since we have a race
        // with the unregister event which may have already been handled, so we'll miss it)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export reverted for {} since service was unregistered", sref);
            endpointRepo.removeService(sref);
            for (ExportRegistration reg : exportRegs) {
                reg.close();
            }
            return;
        }
        // add the new exported endpoints
        if (!endpoints.isEmpty()) {
            LOG.info("TopologyManager: export successful for {}, endpoints: {}", sref, endpoints);
            endpointRepo.addEndpoints(sref, remoteServiceAdmin, endpoints);
        }
    }

    /**
     * Retrieves an exported Endpoint (while safely handling nulls).
     *
     * @param exReg an export registration
     * @return exported Endpoint or null if not present
     */
    private EndpointDescription getExportedEndpoint(ExportRegistration exReg) {
        ExportReference ref = (exReg == null) ? null : exReg.getExportReference();
        return (ref == null) ? null : ref.getExportedEndpoint();
    }


}
