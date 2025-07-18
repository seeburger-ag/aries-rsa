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
package org.apache.aries.rsa.core;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointPermission;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteServiceAdminInstance implements RemoteServiceAdmin {

    // Context of the bundle requesting the RemoteServiceAdmin
    private final BundleContext bctx;
    private final RemoteServiceAdminCore rsaCore;

    private boolean closed;
    private static final Logger LOG = LoggerFactory.getLogger(RemoteServiceAdminInstance.class);

    public RemoteServiceAdminInstance(BundleContext bc, RemoteServiceAdminCore core) {
        bctx = bc;
        rsaCore = core;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ExportRegistration> exportService(final ServiceReference ref, final Map properties) {
        return closed ? Collections.emptyList() : rsaCore.exportService(ref, properties);
    }

    @Override
    public Collection<ExportReference> getExportedServices() {
        checkPermission(new EndpointPermission("*", EndpointPermission.READ));
        return closed ? null : rsaCore.getExportedServices();
    }

    @Override
    public Collection<ImportReference> getImportedEndpoints() {
        checkPermission(new EndpointPermission("*", EndpointPermission.READ));
        return closed ? null : rsaCore.getImportedEndpoints();
    }

    @Override
    public ImportRegistration importService(final EndpointDescription endpoint) {
        String frameworkUUID = Activator.frameworkUUID;
        checkPermission(new EndpointPermission(endpoint, frameworkUUID, EndpointPermission.IMPORT));
        return AccessController.doPrivileged(new PrivilegedAction<ImportRegistration>() {
            public ImportRegistration run() {
                return closed ? null : rsaCore.importService(endpoint);
            }
        });
    }

    public void close(Bundle bundle, boolean closeAll) {
        closed = true;
        try {
            rsaCore.removeExportRegistrations(bctx.getBundle());
            if (closeAll) {
                rsaCore.close();
            }
        }
        catch (Exception e) {
            LOG.warn("Could not shutdown RSA cleanly: {}",e.getMessage());
            LOG.debug("Stacktrace:",e);
        }
    }

    private void checkPermission(EndpointPermission permission) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(permission);
        }
    }
}
