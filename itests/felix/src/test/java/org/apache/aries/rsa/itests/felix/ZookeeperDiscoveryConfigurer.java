package org.apache.aries.rsa.itests.felix;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class ZookeeperDiscoveryConfigurer implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        ServiceTracker<ConfigurationAdmin, Object> tracker = new ServiceTracker<>(context, ConfigurationAdmin.class, null);
        tracker.open();
        ConfigurationAdmin configAdmin = (ConfigurationAdmin)tracker.getService();
        Dictionary<String, Object> cliProps = new Hashtable<String, Object>();
        cliProps.put("zookeeper.host", "127.0.0.1");
        cliProps.put("zookeeper.port", "" + context.getProperty("zkPort"));
        configAdmin.getConfiguration("org.apache.aries.rsa.discovery.zookeeper", null).update(cliProps);
        tracker.close();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }
}
