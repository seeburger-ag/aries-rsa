package org.apache.aries.rsa.examples.fastbin.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public interface EchoService {
    public String echo(String msg);
    
    public CompletableFuture<String> echoAsync(String msg);
    
    public InputStream echoStream(String msg);
    
    public String echoStream2(InputStream msg) throws IOException;
}
