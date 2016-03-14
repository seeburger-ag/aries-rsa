package org.apache.aries.rsa.itests.felix;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.inject.Inject;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.OptionalCompositeOption;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;

public class RsaTestBase {

    @Inject
    BundleContext bundleContext;

    @Inject
    ConfigurationAdmin configAdmin;

    static OptionalCompositeOption localRepo() {
        String localRepo = System.getProperty("maven.repo.local");
        if (localRepo == null) {
            localRepo = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
        }
        return when(localRepo != null)
            .useOptions(vmOption("-Dorg.ops4j.pax.url.mvn.localRepository=" + localRepo));
    }

    static MavenArtifactProvisionOption mvn(String groupId, String artifactId) {
        return mavenBundle().groupId(groupId).artifactId(artifactId).versionAsInProject();
    }

    public void testInstalled() throws Exception {
        for (Bundle bundle : bundleContext.getBundles()) {
            System.out.println(bundle.getBundleId() + " " + bundle.getSymbolicName() + " " + bundle.getState()
                               + " " + bundle.getVersion());
        }
    }

    protected int configureZookeeper() throws IOException, InterruptedException {
        final int zkPort = 12051;
        // getFreePort(); does not seem to work
        System.out.println("*** Port for ZooKeeper Server: " + zkPort);
        updateZkServerConfig(zkPort, configAdmin);
        Thread.sleep(1000); // To avoid exceptions in zookeeper client
        updateZkClientConfig(zkPort, configAdmin);
        return zkPort;
    }

    protected void updateZkClientConfig(final int zkPort, ConfigurationAdmin cadmin) throws IOException {
        Dictionary<String, Object> cliProps = new Hashtable<String, Object>();
        cliProps.put("zookeeper.host", "127.0.0.1");
        cliProps.put("zookeeper.port", "" + zkPort);
        cadmin.getConfiguration("org.apache.aries.rsa.discovery.zookeeper", null).update(cliProps);
    }

    protected void updateZkServerConfig(final int zkPort, ConfigurationAdmin cadmin) throws IOException {
        Dictionary<String, Object> svrProps = new Hashtable<String, Object>();
        svrProps.put("clientPort", zkPort);
        cadmin.getConfiguration("org.apache.aries.rsa.discovery.zookeeper.server", null).update(svrProps);
    }

    protected int getFreePort() throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true); // enables quickly reopening socket on same port
            socket.bind(new InetSocketAddress(0)); // zero finds a free port
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    static InputStream configBundleConsumer() {
        return TinyBundles.bundle()
            .add(ZookeeperDiscoveryConfigurer.class)
            .set(Constants.BUNDLE_ACTIVATOR, ZookeeperDiscoveryConfigurer.class.getName())
            .build(TinyBundles.withBnd());
    }

    static InputStream configBundleServer() {
        return TinyBundles.bundle()
            .add(ZookeeperServerConfigurer.class)
            .set(Constants.BUNDLE_ACTIVATOR, ZookeeperServerConfigurer.class.getName())
            .build(TinyBundles.withBnd());
    }

    static Option rsaTcpZookeeper() {
        return composite(junitBundles(), 
                         localRepo(),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                         systemProperty("zkPort").value("15201"),
                         mvn("org.apache.felix", "org.apache.felix.configadmin"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.core"), 
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.spi"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.topology-manager"),
                         mvn("org.apache.aries.rsa.provider", "org.apache.aries.rsa.provider.tcp"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.local"),
                         mvn("org.apache.zookeeper", "zookeeper"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.zookeeper")
                         //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
            );
    }

}
