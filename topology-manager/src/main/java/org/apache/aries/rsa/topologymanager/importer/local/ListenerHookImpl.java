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
package org.apache.aries.rsa.topologymanager.importer.local;

import java.util.Collection;

import org.apache.aries.rsa.topologymanager.Activator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for service listeners and informs ServiceInterestListener about added and removed interest
 * in services
 */
public class ListenerHookImpl implements ListenerHook {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerHookImpl.class);

    private final BundleContext bctx;
    private final ServiceInterestListener serviceInterestListener;
    private final String frameworkUUID;

    public ListenerHookImpl(BundleContext bc, ServiceInterestListener serviceInterestListener) {
        this.bctx = bc;
        this.frameworkUUID = Activator.frameworkUUID;
        this.serviceInterestListener = serviceInterestListener;
    }

    @Override
    public void added(Collection listeners) {
        LOG.debug("added listeners {}", listeners);
        for (Object listenerInfoObject : listeners) {
            ListenerInfo listenerInfo = (ListenerInfo)listenerInfoObject;
            LOG.debug("Filter {}", listenerInfo.getFilter());

            String className = FilterHelper.getObjectClass(listenerInfo.getFilter());

            if (listenerInfo.getBundleContext().equals(bctx)) {
                LOG.debug("ListenerHookImpl: skipping request from myself");
                continue;
            }

            if (listenerInfo.getFilter() == null) {
                LOG.debug("skipping empty filter");
                continue;
            }

            if (FilterHelper.isClassExcluded(className)) {
                LOG.debug("Skipping import request for excluded class [{}]", className);
                continue;
            }
            String exFilter = extendFilter(listenerInfo.getFilter());
            serviceInterestListener.addServiceInterest(exFilter);
        }
    }

    @Override
    public void removed(Collection listeners) {
        LOG.debug("removed listeners {}", listeners);

        for (Object listenerInfoObject : listeners) {
            ListenerInfo listenerInfo = (ListenerInfo)listenerInfoObject;
            LOG.debug("Filter {}", listenerInfo.getFilter());

            // TODO: determine if service was handled?
            String exFilter = extendFilter(listenerInfo.getFilter());
            serviceInterestListener.removeServiceInterest(exFilter);
        }
    }

    String extendFilter(String filter) {
        return "(&" + filter + "(!(" + RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + frameworkUUID + ")))";
    }
}
