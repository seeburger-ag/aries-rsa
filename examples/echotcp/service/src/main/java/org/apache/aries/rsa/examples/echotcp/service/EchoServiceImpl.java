package org.apache.aries.rsa.examples.echotcp.service;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.osgi.service.component.annotations.Component;

@Component(property={"service.exported.interfaces=*", "port=8201"})
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String msg) {
        return msg;
    }

}
