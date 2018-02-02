package org.apache.aries.rsa.topologymanager;

import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerAdapter;
import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerNotifier;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("deprecation")
final class EndpointListenerTracker extends ServiceTracker<EndpointListener, EndpointListener> {
    private TopologyManagerExport tmExport;

    EndpointListenerTracker(BundleContext context, TopologyManagerExport tmExport) {
        super(context, EndpointListener.class, null);
        this.tmExport = tmExport;
    }

    @Override
    public EndpointListener addingService(ServiceReference<EndpointListener> reference) {
        EndpointListener listener = super.addingService(reference);
        EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
        tmExport.addEPListener(adapter, EndpointListenerNotifier.filtersFromEL(reference));
        return listener;
    }

    @Override
    public void modifiedService(ServiceReference<EndpointListener> reference,
                                EndpointListener listener) {
        super.modifiedService(reference, listener);
        EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
        tmExport.addEPListener(adapter, EndpointListenerNotifier.filtersFromEL(reference));
    }

    @Override
    public void removedService(ServiceReference<EndpointListener> reference,
                               EndpointListener listener) {
        EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
        tmExport.removeEPListener(adapter);
        super.removedService(reference, listener);
    }
}