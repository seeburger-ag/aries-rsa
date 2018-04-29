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
package org.apache.aries.rsa.core.event;

import java.security.AccessController;
import java.security.PrivilegedAction;
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
       final Event event = toEvent(rsaEvent);
       ServiceReference<EventAdmin> sref = this.context.getServiceReference(EventAdmin.class);
       if (sref != null) {
           final EventAdmin eventAdmin = this.context.getService(sref);
           AccessController.doPrivileged(new PrivilegedAction<Void>() {
               public Void run() {
                   eventAdmin.postEvent(event);
                   return null;
               }
           });
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
