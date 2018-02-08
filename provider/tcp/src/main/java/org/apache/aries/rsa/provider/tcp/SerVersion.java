package org.apache.aries.rsa.provider.tcp;

import java.io.Serializable;

import org.osgi.framework.Version;

public class SerVersion implements Serializable {
    private static final long serialVersionUID = 4725855052866235835L;

    private String version;
    
    public SerVersion() {
    }
    
    public SerVersion(Version version) {
        this.version = version.toString();
    }
    
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
}
