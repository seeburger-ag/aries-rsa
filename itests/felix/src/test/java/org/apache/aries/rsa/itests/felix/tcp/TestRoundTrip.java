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


import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.PaxExamRuntime;

@RunWith(PaxExam.class)
public class TestRoundTrip extends RsaTestBase {

    private static TestContainer remoteContainer;

    @Inject
    EchoService echoService;

    public static void startRemote() throws IOException, InterruptedException {
        ExamSystem testSystem = PaxExamRuntime.createTestSystem(remoteConfig());
        remoteContainer = PaxExamRuntime.createContainer(testSystem);
        remoteContainer.start();
    }

    private static Option[] remoteConfig() throws IOException {
        return new Option[] {
            rsaCoreZookeeper(),
            rsaTcp(),
            echoTcpService(),
            configZKServer(),
            configZKConsumer(),
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        startRemote();
        return new Option[] {
                rsaCoreZookeeper(),
                rsaTcp(),
                RsaTestBase.echoTcpConsumer(),
                configZKConsumer()
        };
    }

    @Test
    public void testCall() throws Exception {
        assertEquals("test", echoService.echo("test"));
    }

    public static void shutdownRemote() {
        remoteContainer.stop();
    }
}
