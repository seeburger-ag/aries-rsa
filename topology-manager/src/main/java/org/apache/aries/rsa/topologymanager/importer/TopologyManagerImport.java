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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for remote endpoints using the EndpointListener interface and the EndpointListenerManager.
 * Listens for local service interests using the ListenerHookImpl that calls back through the
 * ServiceInterestListener interface.
 * Manages local creation and destruction of service imports using the available RemoteServiceAdmin services.
 */
public class TopologyManagerImport implements EndpointListener, RemoteServiceAdminListener, ServiceInterestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerImport.class);
    private ExecutorService execService;

    private final EndpointListenerManager endpointListenerManager;
    private final BundleContext bctx;
    private Set<RemoteServiceAdmin> rsaSet;
    private final ListenerHookImpl listenerHook;

    /**
     * If set to false only one service is imported for each import interest even it multiple services are
     * available. If set to true, all available services are imported.
     *
     * TODO: Make this available as a configuration option
     */
    private boolean importAllAvailable = true;

    /**
     * Contains an instance of the Class Import Interest for each distinct import request. If the same filter
     * is requested multiple times the existing instance of the Object increments an internal reference
     * counter. If an interest is removed, the related ServiceInterest object is used to reduce the reference
     * counter until it reaches zero. in this case the interest is removed.
     */
    private final ReferenceCounter<String> importInterestsCounter = new ReferenceCounter<String>();

    /**
     * List of Endpoints by matched filter that were reported by the EndpointListener and can be imported
     */
    private final Map<String /* filter */, List<EndpointDescription>> importPossibilities
        = new HashMap<String, List<EndpointDescription>>();

    /**
     * List of already imported Endpoints by their matched filter
     */
    private final Map<String /* filter */, List<ImportRegistration>> importedServices
        = new HashMap<String, List<ImportRegistration>>();
    

    public TopologyManagerImport(BundleContext bc) {
        this.rsaSet = new HashSet<RemoteServiceAdmin>();
        bctx = bc;
        endpointListenerManager = new EndpointListenerManager(bctx, this);
        execService = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        listenerHook = new ListenerHookImpl(bc, this);
    }
    
    public void start() {
        bctx.registerService(RemoteServiceAdminListener.class, this, null);
        bctx.registerService(ListenerHook.class, listenerHook, null);
        endpointListenerManager.start();
    }

    public void stop() {
        endpointListenerManager.stop();
        execService.shutdown();
        // this is called from Activator.stop(), which implicitly unregisters our registered services
    }

    /* (non-Javadoc)
     * @see org.apache.cxf.dosgi.topologymanager.ServiceInterestListener#addServiceInterest(java.lang.String)
     */
    public void addServiceInterest(String filter) {
        if (importInterestsCounter.add(filter) == 1) {
            endpointListenerManager.extendScope(filter);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.cxf.dosgi.topologymanager.ServiceInterestListener#removeServiceInterest(java.lang.String)
     */
    public void removeServiceInterest(String filter) {
        if (importInterestsCounter.remove(filter) == 0) {
            LOG.debug("last reference to import interest is gone -> removing interest filter: {}", filter);
            endpointListenerManager.reduceScope(filter);
            List<ImportRegistration> irs = remove(filter, importedServices);
            if (irs != null) {
                for (ImportRegistration ir : irs) {
                    ir.close();
                }
            }
        }
    }

    public void endpointAdded(EndpointDescription endpoint, String filter) {
        if (filter == null) {
            LOG.error("Endpoint is not handled because no matching filter was provided!");
            return;
        }
        LOG.debug("importable service added for filter {}, endpoint {}", filter, endpoint);
        addImportPossibility(endpoint, filter);
        triggerImport(filter);
    }

    public void endpointRemoved(EndpointDescription endpoint, String filter) {
        LOG.debug("EndpointRemoved {}", endpoint);
        removeImportPossibility(endpoint, filter);
        triggerImport(filter);
    }

    private void addImportPossibility(EndpointDescription endpoint, String filter) {
        put(filter, importPossibilities, endpoint);
    }

    private void removeImportPossibility(EndpointDescription endpoint, String filter) {
        List<EndpointDescription> endpoints = get(filter, importPossibilities);
        remove(filter, importPossibilities, endpoint);
        if (endpoints.isEmpty()) {
            remove(filter,importPossibilities,null);
        }
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);

        for (String filter : keySet(importPossibilities)) {
            triggerImport(filter);
        }

    }
    
    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }


    private void triggerImport(final String filter) {
        LOG.debug("Import of a service for filter {} was queued", filter);

        execService.execute(new Runnable() {
            public void run() {
                try {
                    unexportNotAvailableServices(filter);
                    importServices(filter);
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                }
                // Notify EndpointListeners? NO!
            }
        });
    }

    private void unexportNotAvailableServices(String filter) {
        List<ImportRegistration> importRegistrations = get(filter, importedServices);
        for (ImportRegistration ir : importRegistrations) {
            EndpointDescription endpoint = ir.getImportReference().getImportedEndpoint();
            if (!isImportPossibilityAvailable(endpoint, filter)) {
                removeImport(ir, null); // also unexports the service
            }
        }
    }

    private boolean isImportPossibilityAvailable(EndpointDescription endpoint, String filter) {
        List<EndpointDescription> endpoints = get(filter, importPossibilities);
        return endpoints != null && endpoints.contains(endpoint);

    }

    private void importServices(String filter) {
        List<ImportRegistration> importRegistrations = get(filter, importedServices);
        for (EndpointDescription endpoint : get(filter, importPossibilities)) {
            // TODO but optional: if the service is already imported and the endpoint is still
            // in the list of possible imports check if a "better" endpoint is now in the list
            if (!alreadyImported(endpoint, importRegistrations)) {
                // service not imported yet -> import it now
                ImportRegistration ir = importService(endpoint);
                if (ir != null) {
                    // import was successful
                    put(filter, importedServices, ir);
                    if (!importAllAvailable) {
                        return;
                    }
                }
            }
        }
    }

    private boolean alreadyImported(EndpointDescription endpoint, List<ImportRegistration> importRegistrations) {
        if (importRegistrations != null) {
            for (ImportRegistration ir : importRegistrations) {
                if (endpoint.equals(ir.getImportReference().getImportedEndpoint())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tries to import the service with each rsa until one import is successful
     *
     * @param endpoint endpoint to import
     * @return import registration of the first successful import
     */
    private ImportRegistration importService(EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration ir = rsa.importService(endpoint);
            if (ir != null) {
                if (ir.getException() == null) {
                    LOG.debug("Service import was successful {}", ir);
                    return ir;
                } else {
                    LOG.info("Error importing service " + endpoint, ir.getException());
                }
            }
        }
        return null;
    }

    /**
     * Remove and close (unexport) the given import. The import is specified either
     * by its ImportRegistration or by its ImportReference (only one of them must
     * be specified).
     * <p>
     * If this method is called from within iterations on the underlying data structure,
     * the iterations must be made on copies of the structures rather than the original
     * references in order to prevent ConcurrentModificationExceptions.
     *
     * @param reg the import registration to remove
     * @param ref the import reference to remove
     */
    private void removeImport(ImportRegistration reg, ImportReference ref) {
        // this method may be called recursively by calling ImportRegistration.close()
        // and receiving a RemoteServiceAdminEvent for its unregistration, which results
        // in a ConcurrentModificationException. We avoid this by closing the registrations
        // only after data structure manipulation is done, and being re-entrant.
        List<ImportRegistration> removed = new ArrayList<ImportRegistration>();
        Set<Entry<String, List<ImportRegistration>>> entries = entrySet(importedServices);
        for (Entry<String, List<ImportRegistration>> entry : entries) {
            for (ImportRegistration ir : entry.getValue()) {
                if (ir.equals(reg) || ir.getImportReference().equals(ref)) {
                    removed.add(ir);
                    remove(entry.getKey(), importedServices, ir);
                }
            }
        }
        for (ImportRegistration ir : removed) {
            ir.close();
        }
    }

    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION) {
            removeImport(null, event.getImportReference());
        }
    }

    private <T> void put(String key, Map<String, List<T>> map, T value) {
        synchronized (map) {
            List<T> list = map.get(key);
            if(list == null) {
                list = new CopyOnWriteArrayList<T>();
                map.put(key, list);
            }
            //make sure there is no duplicates
            if(!list.contains(value)) {
                list.add(value);
            }
        }
    }

    private <T> List<T> get(String key, Map<String, List<T>> map) {
        synchronized (map) {
            List<T> list = map.get(key);
            if(list == null)
                return Collections.emptyList();
            return list;
        }
    }

    private <T> List<T> remove(String key, Map<String, List<T>> map) {
        synchronized (map) {
            return map.remove(key);
        }
    }

    private <T> void remove(String key, Map<String, List<T>> map, T value) {
        synchronized (map) {
            List<T> list = map.get(key);
            if (list != null) {
                list.remove(value);
                if(list.isEmpty()) {
                    map.remove(key);
                }
            }
        }
    }

    private <T> Set<Entry<String, List<T>>> entrySet(Map<String, List<T>> map) {
        synchronized (map) {
            Set<Entry<String, List<T>>> entries = map.entrySet();
            return new HashSet<Entry<String, List<T>>>(entries);
        }
    }

    private <T> Set<String> keySet(Map<String, List<T>> map) {
        synchronized (map) {
            Set<String> keySet = map.keySet();
            return new HashSet<String>(keySet);
        }
    }
}
