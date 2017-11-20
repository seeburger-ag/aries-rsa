package org.apache.aries.rsa.examples.fastbin.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.aries.rsa.examples.fastbin.api.EchoService;
import org.osgi.service.component.annotations.Component;

@Component(//
    property = {
                "service.exported.interfaces=*",
                "service.exported.configs=aries.fastbin"
    })
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String msg) {
        return msg;
    }

	@Override
	public CompletableFuture<String> echoAsync(final String msg) {
	 	return CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			return msg;
		});
	}

	@Override
	public InputStream echoStream(String msg) {
		return new ByteArrayInputStream(msg.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String echoStream2(InputStream msg) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = msg.read(buffer)) != -1) {
		    result.write(buffer, 0, length);
		}
		return result.toString("UTF-8");
	}

}
