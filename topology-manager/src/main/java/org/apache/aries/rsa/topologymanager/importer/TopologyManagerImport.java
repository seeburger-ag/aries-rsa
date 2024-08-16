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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for remote endpoints using the EndpointListener. The scope of this listener is managed by
 * the EndpointListenerManager.
 * Manages local creation and destruction of service imports using the available RemoteServiceAdmin services.
 */
public class TopologyManagerImport implements EndpointEventListener, RemoteServiceAdminListener {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerImport.class);

    private final ExecutorService execService;
    private final BundleContext bctx;
    private final Set<RemoteServiceAdmin> rsaSet;
    private volatile boolean stopped;

    /**
     * List of Endpoints by matched filter that were reported by the EndpointListener and can be imported
     */
    private final MultiMap<String, EndpointDescription> importPossibilities = new MultiMap<>();

    /**
     * List of already imported Endpoints by their matched filter
     */
    private final MultiMap<String, ImportRegistration> importedServices = new MultiMap<>();

    public TopologyManagerImport(BundleContext bc) {
        this.rsaSet = new CopyOnWriteArraySet<>();
        bctx = bc;
        execService = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(getClass()));
    }

    public void start() {
        stopped = false;
        bctx.registerService(RemoteServiceAdminListener.class.getName(), this, null);
    }

    public void stop() {
        stopped = true;
        execService.shutdown();
        try {
            execService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for {} to terminate", execService);
            Thread.currentThread().interrupt();
        }
        // close all imports
        importPossibilities.clear();
        importedServices.allValues().forEach(this::unimportRegistration);
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        importPossibilities.keySet().forEach(this::synchronizeImportsAsync);
    }

    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        ImportReference ref = event.getImportReference();
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION && ref != null) {
            importedServices.allValues().stream()
                .filter(ir -> ref.equals(ir.getImportReference()))
                .forEach(this::unimportRegistration);
        }
    }

    private void synchronizeImportsAsync(final String filter) {
        LOG.debug("Import of a service for filter {} was queued", filter);
        if (!rsaSet.isEmpty()) {
            execService.execute(() -> synchronizeImports(filter));
        }
    }

    /**
     * Synchronizes the actual imports with the possible imports for the given filter,
     * i.e. unimports previously imported endpoints that are no longer possible,
     * and imports new possible endpoints that are not already imported.
     * 
     * TODO but optional: if the service is already imported and the endpoint is still
     * in the list of possible imports check if a "better" endpoint is now in the list
     *
     * @param filter the filter whose endpoints are synchronized
     */
    private void synchronizeImports(final String filter) {
        try {
            ImportDiff diff = new ImportDiff(importPossibilities.get(filter), importedServices.get(filter));
            diff.getRemoved()
                .forEach(this::unimportRegistration);
            diff.getAdded()
                .flatMap(this::importService)
                .forEach(ir -> importedServices.put(filter, ir));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        // Notify EndpointListeners? NO!
    }

    /**
     * Tries to import the service with each rsa until one import is successful.
     *
     * @param filter the filter that matched the endpoint
     * @param endpoint endpoint to import
     * @return 
     */
    private Stream<ImportRegistration> importService(EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration ir = rsa.importService(endpoint);
            if (ir != null) {
                if (ir.getException() == null) {
                    LOG.debug("Service import was successful {}", ir);
                    return Stream.of(ir);
                } else {
                    LOG.info("Error importing service {}", endpoint, ir.getException());
                }
            }
        }
        return Stream.empty();
    }
    
    private void unimportRegistration(ImportRegistration reg) {
        importedServices.remove(reg);
        reg.close();
    }
    
    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        if (stopped) {
            return;
        }
        EndpointDescription endpoint = event.getEndpoint();
        LOG.debug("Endpoint event received type {}, filter {}, endpoint {}", event.getType(), filter, endpoint);
        switch (event.getType()) {
            case EndpointEvent.ADDED:
                importPossibilities.put(filter, endpoint);
                break;
            case EndpointEvent.REMOVED:
            case EndpointEvent.MODIFIED_ENDMATCH:
                importPossibilities.remove(filter, endpoint);
                break;
            case EndpointEvent.MODIFIED:
                importPossibilities.remove(filter, endpoint);
                importPossibilities.put(filter, endpoint);
                break;
        }
        synchronizeImportsAsync(filter);
    }

}
