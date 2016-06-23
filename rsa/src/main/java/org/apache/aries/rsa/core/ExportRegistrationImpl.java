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
package org.apache.aries.rsa.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ExportRegistrationImpl implements ExportRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(ExportRegistrationImpl.class);

    private final RemoteServiceAdminCore rsaCore;
    private final ExportReferenceImpl exportReference;
    private final Closeable server;
    private final Throwable exception;

    private final ExportRegistrationImpl parent;
    private int instanceCount;
    private volatile boolean closed;

    private ExportRegistrationImpl(ExportRegistrationImpl parent, RemoteServiceAdminCore rsaCore,
            ExportReferenceImpl exportReference, Closeable server, Throwable exception) {
        this.parent = parent != null ? parent.parent : this; // a parent points to itself
        this.parent.addInstance();
        this.rsaCore = rsaCore;
        this.exportReference = exportReference;
        this.server = server;
        this.exception = exception;
    }

    // create a clone of the provided ExportRegistrationImpl that is linked to it
    public ExportRegistrationImpl(ExportRegistrationImpl parent) {
        this(parent, parent.rsaCore, new ExportReferenceImpl(parent.exportReference),
            parent.server, parent.exception);
    }

    // create a new (parent) instance which was exported successfully with the given server
    public ExportRegistrationImpl(ServiceReference sref, Endpoint endpoint, RemoteServiceAdminCore rsaCore) {
        this(null, rsaCore, new ExportReferenceImpl(sref, endpoint.description()), endpoint, null);
    }

    // create a new (parent) instance which failed to be exported with the given exception
    public ExportRegistrationImpl(RemoteServiceAdminCore rsaCore, Throwable exception) {
        this(null, rsaCore, null, null, exception);
    }

    private void ensureParent() {
        if (parent != this) {
            throw new IllegalStateException("this method may only be called on the parent");
        }
    }

    /**
     * Returns the ExportReference even if this
     * instance is closed or has an exception.
     *
     * @return the export reference
     */
    public ExportReference getExportReferenceAlways() {
        return exportReference;
    }

    public ExportReference getExportReference() {
        if (exportReference == null) {
            throw new IllegalStateException(getException());
        }
        return closed ? null : exportReference;
    }

    public Throwable getException() {
        return closed ? null : exception;
    }

    public final void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }

        rsaCore.removeExportRegistration(this);
        if (exportReference != null) {
            exportReference.close();
        }
        parent.removeInstance();
    }

    private void addInstance() {
        ensureParent();
        synchronized (this) {
            instanceCount++;
        }
    }

    private void removeInstance() {
        ensureParent();
        synchronized (this) {
            instanceCount--;
            if (instanceCount <= 0) {
                LOG.debug("really closing ExportRegistration now!");

                if (server != null) {
                    try {
                        server.close();
                    } catch (IOException e) {
                        LOG.warn("Error closing ExportRegistration", e);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        if (closed) {
            return "ExportRegistration closed";
        }
        EndpointDescription endpoint = getExportReference().getExportedEndpoint();
        ServiceReference serviceReference = getExportReference().getExportedService();
        String r = "EndpointDescription for ServiceReference " + serviceReference;

        r += "\n*** EndpointDescription: ****\n";
        if (endpoint == null) {
            r += "---> NULL <---- \n";
        } else {
            Set<Map.Entry<String, Object>> props = endpoint.getProperties().entrySet();
            for (Map.Entry<String, Object> entry : props) {
                Object value = entry.getValue();
                r += entry.getKey() + " => "
                    + (value instanceof Object[] ? Arrays.toString((Object[]) value) : value) + "\n";
            }
        }
        return r;
    }
}
