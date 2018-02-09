package org.apache.aries.rsa.provider.tcp.ser;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.osgi.framework.Version;

public class BasicObjectOutputStream extends ObjectOutputStream {

    public BasicObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (obj instanceof Version) {
            return new VersionMarker((Version) obj);
        } else {
            return super.replaceObject(obj);
        }
    }
}
