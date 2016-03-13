package org.apache.aries.rsa.itests.tcp.service;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.itests.tcp.api.EchoService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        EchoService echoService = new EchoServiceImpl();
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(RemoteConstants.SERVICE_EXPORTED_INTERFACES, "*");
        context.registerService(EchoService.class, echoService, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
   }

}
