package org.apache.aries.rsa.core.event;

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;

public class EventAdminSender {
    private HashMap<Integer, String> typeToTopic;
    private BundleContext context;
    
    public EventAdminSender(BundleContext context) {
        this.context = context;
        typeToTopic = new HashMap<>();
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_ERROR, "EXPORT_ERROR");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_REGISTRATION, "EXPORT_REGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION, "EXPORT_UNREGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_UPDATE, "EXPORT_UPDATE");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_WARNING, "EXPORT_WARNING");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_ERROR, "IMPORT_ERROR");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_REGISTRATION, "IMPORT_REGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION, "IMPORT_UNREGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_UPDATE, "IMPORT_UPDATE");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_WARNING, "IMPORT_WARNING");
    }

    public void send(RemoteServiceAdminEvent rsaEvent) {
       Event event = toEvent(rsaEvent);
       ServiceReference<EventAdmin> sref = this.context.getServiceReference(EventAdmin.class);
       if (sref != null) {
           EventAdmin eventAdmin = this.context.getService(sref);
           eventAdmin.postEvent(event);
           this.context.ungetService(sref);           
       }
    }

    private Event toEvent(RemoteServiceAdminEvent rsaEvent) {
        String topic = getTopic(rsaEvent);
        Map<String, Object> props = new HashMap<>();
        props.put("bundle", rsaEvent.getSource());
        props.put("bundle.id", rsaEvent.getSource().getBundleId());
        props.put("bundle.symbolicname", rsaEvent.getSource().getSymbolicName());
        props.put("bundle.version", rsaEvent.getSource().getVersion());
        props.put("bundle.signer", ""); // TODO What to put here
        if (rsaEvent.getException() != null) {
            props.put("exception", rsaEvent.getException());
            props.put("exception.class", rsaEvent.getException().getClass());
            props.put("exception.class", rsaEvent.getException().getMessage());
        }
        if (rsaEvent.getExportReference() != null) {
            EndpointDescription endpoint = rsaEvent.getExportReference().getExportedEndpoint();
            props.put("endpoint.framework.uuid", endpoint.getFrameworkUUID());
            props.put("endpoint.id", endpoint.getId());
            props.put("objectClass", endpoint.getInterfaces());
        }
        if (rsaEvent.getImportReference() != null && rsaEvent.getImportReference().getImportedEndpoint() != null) {
            props.put("service.imported.configs", rsaEvent.getImportReference().getImportedEndpoint().getConfigurationTypes());
        }
        props.put("timestamp", System.currentTimeMillis());
        props.put("event", rsaEvent);
        return new Event(topic, props);
    }

    private String getTopic(RemoteServiceAdminEvent rsaEvent) {
        return "org/osgi/service/remoteserviceadmin/" + typeToTopic.get(rsaEvent.getType());
    }
}
