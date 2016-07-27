/*
 * Activator.java
 *
 * created at 31.05.2016 by utzig <j.utzig@seeburger.de>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.aries.rsa.provider.fastbin;

import java.util.Hashtable;

import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class Activator implements BundleActivator
{

    FastBinProvider fastBinProvider;
    ClientInvoker client;
    ServerInvoker server;
    static Activator INSTANCE;

    @Override
    public void start(BundleContext context) throws Exception
    {
        INSTANCE = this;
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, new String[]{""});
        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, new String[]{FastBinProvider.CONFIG_NAME});
        fastBinProvider = new FastBinProvider();
        fastBinProvider.activate(context, props);
        this.client = fastBinProvider.getClient();
        this.server = fastBinProvider.getServer();
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        if(fastBinProvider!=null)
            fastBinProvider.deactivate();
    }

    public static Activator getInstance() {
        return INSTANCE;
    }

    public ClientInvoker getClient() {
        return client;
    }

    public ServerInvoker getServer() {
        return server;
    }

}



