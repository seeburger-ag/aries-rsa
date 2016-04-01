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

import javax.inject.Inject;

import org.apache.aries.rsa.itests.felix.helpers.ZookeeperDiscoveryConfigurer;
import org.apache.aries.rsa.itests.felix.helpers.ZookeeperServerConfigurer;
import org.ops4j.pax.exam.CoreOptions;
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

    static Option echoTcpConsumer() {
        return CoreOptions.composite(
        mvn("org.apache.felix", "org.apache.felix.scr"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api"),
        // Consumer is needed to trigger service import. Pax exam inject does not work for it
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.consumer")
        );
    }

    static Option echoTcpService() {
        return CoreOptions.composite(
        mvn("org.apache.felix", "org.apache.felix.scr"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.service")
        );
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
                         systemProperty("aries.rsa.hostname").value("localhost"),
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
