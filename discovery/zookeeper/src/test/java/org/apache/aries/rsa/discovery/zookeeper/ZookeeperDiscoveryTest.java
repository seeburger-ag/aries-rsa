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
package org.apache.aries.rsa.discovery.zookeeper;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.discovery.zookeeper.ZooKeeperDiscovery;
import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;


public class ZookeeperDiscoveryTest {

    @Test
    public void testDefaults() throws ConfigurationException {
        IMocksControl c = EasyMock.createControl();
        BundleContext bctx = c.createMock(BundleContext.class);
        ZooKeeperDiscovery zkd = new ZooKeeperDiscovery(bctx) {
            @Override
            protected ZooKeeper createZooKeeper(String host, String port, int timeout) {
                Assert.assertEquals("localhost", host);
                Assert.assertEquals("2181", port);
                Assert.assertEquals(3000, timeout);
                return null;
            }  
        };
        
        Dictionary<String, Object> configuration = new Hashtable<String, Object>();
        zkd.updated(configuration);
    }
    
    @Test
    public void testConfig() throws ConfigurationException {
        IMocksControl c = EasyMock.createControl();
        BundleContext bctx = c.createMock(BundleContext.class);
        ZooKeeperDiscovery zkd = new ZooKeeperDiscovery(bctx) {
            @Override
            protected ZooKeeper createZooKeeper(String host, String port, int timeout) {
                Assert.assertEquals("myhost", host);
                Assert.assertEquals("1", port);
                Assert.assertEquals(1000, timeout);
                return null;
            }  
        };
        
        Dictionary<String, Object> configuration = new Hashtable<String, Object>();
        configuration.put("zookeeper.host", "myhost");
        configuration.put("zookeeper.port", "1");
        configuration.put("zookeeper.timeout", "1000");
        zkd.updated(configuration);
    }
}
