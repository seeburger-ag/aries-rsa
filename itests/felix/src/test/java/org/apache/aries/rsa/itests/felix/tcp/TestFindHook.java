package org.apache.aries.rsa.itests.felix.tcp;
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


import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.itests.felix.ServerConfiguration;
import org.apache.aries.rsa.itests.felix.TwoContainerPaxExam;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

@RunWith(TwoContainerPaxExam.class)
public class TestFindHook extends RsaTestBase {

    @Inject
    BundleContext context;

    @ServerConfiguration
    public static Option[] remoteConfig() throws IOException {
        return new Option[] {
            rsaCore(),
            rsaDiscoveryZookeeper(),
            rsaProviderTcp(),
            echoTcpService(),
            configZKServer(),
            configZKDiscovery(),
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaDiscoveryZookeeper(),
                rsaProviderTcp(),
                echoTcpAPI(),
                configZKDiscovery()
        };
    }
    
    public <T> T tryTo(String message, Callable<T> func) throws TimeoutException {
        return tryTo(message, func, 5000);
    }
    
    public <T> T tryTo(String message, Callable<T> func, long timeout) throws TimeoutException {
        Throwable lastException = null;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                T result = func.call();
                if (result != null) {
                    return result;
                }
                lastException = null;
            } catch (Throwable e) {
                lastException = e;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                continue;
            }
        }
        TimeoutException ex = new TimeoutException("Timeout while trying to " + message);
        if (lastException != null) {
            ex.addSuppressed(lastException);
        }
        throw ex;
    }

    @Test
    public void testFind() throws Exception {
        ServiceReference<EchoService> ref = tryTo("get EchoService", new Callable<ServiceReference<EchoService>>() {

            @Override
            public ServiceReference<EchoService> call() throws Exception {
                Collection<ServiceReference<EchoService>> refs = context.getServiceReferences(EchoService.class, null);
                return (refs.size() > 0)? refs.iterator().next() : null;
            }
        }, 10000);
        Assert.assertNotNull(ref);
    }

}
