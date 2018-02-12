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

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

@RunWith(PaxExam.class)
public class TestDiscoveryExport extends RsaTestBase {
    private static final String GREETER_ZOOKEEPER_NODE = "/osgi/service_registry";

    @Inject
    DistributionProvider tcpProvider;

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaDiscoveryZookeeper(),
                rsaProviderTcp(),
                echoTcpService(),
                localRepo(),
                configZKDiscovery(),
                configZKServer()
        };
    }

    @Test
    public void testDiscoveryExport() throws Exception {
        EndpointDescription epd = getEndpoint();
        EchoService service = (EchoService)tcpProvider
            .importEndpoint(EchoService.class.getClassLoader(), 
                            bundleContext, new Class[]{EchoService.class}, epd);
        Assert.assertEquals("test", service.echo("test"));
    }

    private EndpointDescription getEndpoint() throws Exception {
        ZooKeeper zk = new ZooKeeper("localhost:" + ZK_PORT, 1000, new DummyWatcher());
        assertNodeExists(zk, GREETER_ZOOKEEPER_NODE, 10000);
        String endpointPath = getEndpointPath(zk, GREETER_ZOOKEEPER_NODE);
        EndpointDescription epd = getEndpointDescription(zk, endpointPath);
        zk.close();
        return epd;
    }

    private EndpointDescription getEndpointDescription(ZooKeeper zk, String endpointPath)
        throws KeeperException, InterruptedException {
        byte[] data = zk.getData(endpointPath, false, null);
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        return new EndpointDescriptionParser().readEndpoint(is);
    }

    private String getEndpointPath(ZooKeeper zk, String servicePath) throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(servicePath, false);
        return servicePath + "/" + children.iterator().next();
    }

    private void assertNodeExists(ZooKeeper zk, String zNode, int timeout) {
        long endTime = System.currentTimeMillis() + timeout;
        Stat stat = null;
        while (stat == null && System.currentTimeMillis() < endTime) {
            try {
                stat = zk.exists(zNode, null);
                Thread.sleep(200);
            } catch (Exception e) {
                // Ignore
            }
        }
        Assert.assertNotNull("ZooKeeper node " + zNode + " was not found", stat);
    }
    
    private final class DummyWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
        }
    }

}
