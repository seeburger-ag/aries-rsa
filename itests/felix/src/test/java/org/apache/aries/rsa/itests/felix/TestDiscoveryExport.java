package org.apache.aries.rsa.itests.felix;
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


import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.aries.rsa.discovery.endpoint.PropertiesMapper;
import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.xmlns.rsa.v1_0.EndpointDescriptionType;

@RunWith(PaxExam.class)
public class TestDiscoveryExport extends RsaTestBase {
    private static final String GREETER_ZOOKEEPER_NODE = "/osgi/service_registry/org/apache/aries/rsa/examples/echotcp/api/EchoService";

    @Inject
    DistributionProvider tcpProvider;

    @Configuration
    public static Option[] configure() throws Exception {

        return new Option[] {
                CoreOptions.junitBundles(),
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                RsaTestBase.rsaTcpZookeeper(),
                mvn("org.apache.felix", "org.apache.felix.scr"),
                mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api"),
                mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.service"),
                localRepo(),
                streamBundle(configBundleServer())
                //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
        };
    }

    @Test
    public void testDiscoveryExport() throws Exception {
        String zkPort = bundleContext.getProperty("zkPort");
        ZooKeeper zk = new ZooKeeper("localhost:" + zkPort, 1000, new DummyWatcher());
        assertNodeExists(zk, GREETER_ZOOKEEPER_NODE, 10000);
        List<String> children = zk.getChildren(GREETER_ZOOKEEPER_NODE, false);
        EndpointDescriptionParser parser = new EndpointDescriptionParser();
        String path = children.get(0);
        byte[] data = zk.getData(GREETER_ZOOKEEPER_NODE + "/" + path, false, null);
        List<EndpointDescriptionType> epdList = parser.getEndpointDescriptions(new ByteArrayInputStream(data));
        Map<String, Object> props = new PropertiesMapper().toProps(epdList.get(0).getProperty());
        EndpointDescription epd = new EndpointDescription(props);
        EchoService service = (EchoService)tcpProvider.importEndpoint(EchoService.class.getClassLoader(), bundleContext, new Class[]{EchoService.class}, epd);
        String answer = service.echo("test");
        Assert.assertEquals("test", answer);
        zk.close();
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
