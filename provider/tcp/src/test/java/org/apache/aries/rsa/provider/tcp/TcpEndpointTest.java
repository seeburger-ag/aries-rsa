package org.apache.aries.rsa.provider.tcp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.provider.tcp.myservice.MyService;
import org.apache.aries.rsa.provider.tcp.myservice.MyServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class TcpEndpointTest {

    @Test
    public void testEndpointProperties() throws IOException {
        Object service = new MyServiceImpl();
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[]{MyService.class.getName()});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "");
        props.put("port", "45346");
        props.put("hostname", "myhost");
        TcpEndpoint tcpEndpoint = new TcpEndpoint(service, props);
        EndpointDescription epd = tcpEndpoint.description();
        Assert.assertEquals("tcp://myhost:45346", epd.getId());
        tcpEndpoint.close();
    }
    
    @Test
    public void testEndpointPropertiesDefault() throws IOException {
        Object service = new MyServiceImpl();
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[]{MyService.class.getName()});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "");
        TcpEndpoint tcpEndpoint = new TcpEndpoint(service, props);
        EndpointDescription epd = tcpEndpoint.description();
        Assert.assertNotNull(epd.getId());
        tcpEndpoint.close();
    }
}
