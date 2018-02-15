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
package org.apache.aries.rsa.itests.felix.tcp;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

@RunWith(PaxExam.class)
public class TestDiscoveryImport extends RsaTestBase {
    @Inject
    ZookeeperEndpointRepository repository;
    
    @Inject
    BundleContext context;
    
    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaProviderTcp(),
                rsaDiscoveryZookeeper(),
                localRepo(),
                configZKDiscovery(),
                configZKServer()
        };
    }

    @Ignore
    @Test
    public void testDiscoveryImport() throws Exception {
        final Semaphore sem = new Semaphore(0);
        final List<EndpointEvent> events = new ArrayList<>();
        EndpointEventListener listener = new EndpointEventListener() {
            
            @Override
            public void endpointChanged(EndpointEvent event, String filter) {
                events.add(event);
                sem.release();
            }
        };
        Dictionary<String, Object> eprops = new Hashtable<>();
        eprops.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, "(objectClass=*)");
        context.registerService(EndpointEventListener.class, listener, eprops);
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[]{"my"});
        props.put(RemoteConstants.ENDPOINT_ID, "myid");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "myconfig");
        EndpointDescription endpoint = new EndpointDescription(props);
        repository.add(endpoint);
        assertTrue(sem.tryAcquire(10, TimeUnit.SECONDS));
        //assertThat(events.get(0), samePropertyValuesAs(new EndpointEvent(EndpointEvent.ADDED, endpoint)));
    }

}
