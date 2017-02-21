package org.apache.aries.rsa.itests.felix.rsa;
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


import static org.junit.Assert.*;

import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

@RunWith(PaxExam.class)
public class TestRSAListener extends RsaTestBase implements RemoteServiceAdminListener {

    @Inject
    BundleContext context;

    private RemoteServiceAdminEvent lastEvent;


    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaFastBin(),
                echoTcpService(),
                configFastBin("2545"),
        };
    }

    @Test
    public void testListener() throws Exception {
        Bundle serviceBundle = getBundle("org.apache.aries.rsa.examples.echotcp.service");
        serviceBundle.stop();
        ServiceRegistration<RemoteServiceAdminListener> sreg = context.registerService(RemoteServiceAdminListener.class, this, null);

        serviceBundle.start();
        assertEvent(RemoteServiceAdminEvent.EXPORT_REGISTRATION);

        serviceBundle.stop();
        assertEvent(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);

        sreg.unregister();
    }

    @Override
    public synchronized void remoteAdminEvent(RemoteServiceAdminEvent event) {
        lastEvent = event;
        this.notifyAll();
    }

    private void assertEvent(int eventType) throws InterruptedException, TimeoutException {
        waitEvent();
        assertEquals(eventType, lastEvent.getType());
        assertNotNull("ExportReference must be available",lastEvent.getExportReference());
        this.lastEvent = null;
    }

    private synchronized void waitEvent() throws InterruptedException, TimeoutException {
        long start = System.currentTimeMillis();
        while (this.lastEvent == null) {
            this.wait(1000);
            if (System.currentTimeMillis() - start > 2000) {
                throw new TimeoutException("Timeout waiting for Event");
            }
        }
    }

    private Bundle getBundle(String symName) {
        Bundle serviceBundle = null;
        Bundle[] bundles = context.getBundles();
        for (Bundle bundle : bundles) {
            if(symName.equals(bundle.getSymbolicName())) {
                serviceBundle = bundle;
                break;
            }
        }
        return serviceBundle;
    }

}
