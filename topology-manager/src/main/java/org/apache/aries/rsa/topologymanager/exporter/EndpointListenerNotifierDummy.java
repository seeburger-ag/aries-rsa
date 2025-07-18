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
package org.apache.aries.rsa.topologymanager.exporter;


import org.osgi.framework.Filter;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;


/**
 * Dummy implementation of the EndpointListenerNotifier.
 */
public class EndpointListenerNotifierDummy extends EndpointListenerNotifier
{
    private static final Logger LOG = LoggerFactory.getLogger(EndpointListenerNotifierDummy.class);

    public EndpointListenerNotifierDummy() {
        // No initialization needed for dummy implementation
    }

    public void add(EndpointEventListener ep, Set<Filter> filters, Collection<EndpointDescription> endpoints) {
        LOG.debug("EndpointListener not added for EndpointListenerNotifierDummy");
    }

    public void remove(EndpointEventListener ep) {
        LOG.debug("EndpointListener not removed for EndpointListenerNotifierDummy");
    }

    public void sendEvent(EndpointEvent event) {
        // Dummy implementation does not send events
    }

}
