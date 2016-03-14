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
