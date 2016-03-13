package org.apache.aries.rsa.itests.tcp.service;

import org.apache.aries.rsa.itests.tcp.api.EchoService;

public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String msg) {
        return msg;
    }

}
