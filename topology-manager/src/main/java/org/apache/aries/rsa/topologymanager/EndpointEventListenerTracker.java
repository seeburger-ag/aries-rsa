package org.apache.aries.rsa.topologymanager;

import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerNotifier;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.util.tracker.ServiceTracker;

final class EndpointEventListenerTracker extends ServiceTracker<EndpointEventListener, EndpointEventListener> {
    private TopologyManagerExport tmExport;

    EndpointEventListenerTracker(BundleContext context, TopologyManagerExport tmExport) {
        super(context, EndpointEventListener.class, null);
        this.tmExport = tmExport;
    }

    @Override
    public EndpointEventListener addingService(ServiceReference<EndpointEventListener> reference) {
        EndpointEventListener listener = super.addingService(reference);
        this.tmExport.addEPListener(listener, EndpointListenerNotifier.filtersFromEEL(reference));
        return listener;
    }

    @Override
    public void modifiedService(ServiceReference<EndpointEventListener> reference,
            EndpointEventListener listener) {
        this.tmExport.addEPListener(listener, EndpointListenerNotifier.filtersFromEEL(reference));
        super.modifiedService(reference, listener);
    }

    @Override
    public void removedService(ServiceReference<EndpointEventListener> reference,
            EndpointEventListener listener) {
        this.tmExport.removeEPListener(listener);
        super.removedService(reference, listener);
    }
    
}