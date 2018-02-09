package org.apache.aries.rsa.provider.tcp.ser;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.osgi.framework.Version;

public class BasicObjectOutputStream extends ObjectOutputStream {

    public BasicObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                enableReplaceObject(true);
                return null;
            }
        });
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (obj instanceof Serializable || obj.getClass().isArray()) {
            return obj;
        } else if (obj instanceof Version) {
            return new VersionMarker((Version) obj);
        } else {
            return new DTOMarker(obj);
        }
    }
}
