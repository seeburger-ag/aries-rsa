package org.apache.aries.rsa.itests.felix.discovery.config;
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


import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.itests.felix.ServerConfiguration;
import org.apache.aries.rsa.itests.felix.TwoContainerPaxExam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

@RunWith(TwoContainerPaxExam.class)
public class TestConfigDiscoveryRoundTrip extends RsaTestBase {

    @Inject
    EchoService echoService;

    @ServerConfiguration
    public static Option[] remoteConfig() throws IOException {
        return new Option[] {
            rsaCore(),
            rsaTcp(),
            echoTcpService()
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaDiscoveryConfig(),
                rsaTcp(),
                echoTcpConsumer(),
                configTcpConfigDiscovery()
        };
    }


    protected static Option configTcpConfigDiscovery() {
        int freePort = 8201;
        try {
            freePort = getFreePort();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return factoryConfiguration("org.apache.aries.rsa.discovery.config")
            .put("service.imported", "true")
            .put("service.imported.configs", "aries.tcp")
            .put("objectClass", "org.apache.aries.rsa.examples.echotcp.api.EchoService")
            .put("endpoint.id", "tcp://localhost:"+freePort)
            .put("aries.tcp.hostname", "localhost")
            .put("aries.tcp.port", String.valueOf(freePort))
            .asOption();
    }

    @Test
    public void testCall() throws Exception {
        assertEquals("test", echoService.echo("test"));
    }

}
