package org.apache.aries.rsa.provider.tcp.ser;

import java.io.Serializable;

import org.osgi.framework.Version;

public class VersionMarker implements Serializable {
    private static final long serialVersionUID = 4725855052866235835L;

    private String version;
    
    public VersionMarker() {
    }
    
    public VersionMarker(Version version) {
        this.version = version.toString();
    }
    
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
}
