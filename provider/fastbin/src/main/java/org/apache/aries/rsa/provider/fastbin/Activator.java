/*
 * Activator.java
 *
 * created at 31.05.2016 by utzig <j.utzig@seeburger.de>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.aries.rsa.provider.fastbin;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class Activator implements BundleActivator
{

    private FastBinProvider fastBinProvider;

    @Override
    public void start(BundleContext context) throws Exception
    {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, new String[]{""});
        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, new String[]{FastBinProvider.CONFIG_NAME});
        fastBinProvider = new FastBinProvider();
        fastBinProvider.activate(context, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        if(fastBinProvider!=null)
            fastBinProvider.deactivate();
    }

}



