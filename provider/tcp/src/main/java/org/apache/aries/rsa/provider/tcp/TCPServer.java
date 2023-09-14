/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.provider.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

import org.apache.aries.rsa.provider.tcp.ser.BasicObjectOutputStream;
import org.apache.aries.rsa.provider.tcp.ser.BasicObjectInputStream;
import org.osgi.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TCPServer implements Closeable, Runnable {
    private Logger log = LoggerFactory.getLogger(TCPServer.class);
    private ServerSocket serverSocket;
    private Object service;
    private volatile boolean running;
    private ExecutorService executor;
    private MethodInvoker invoker;

    public TCPServer(Object service, String localip, Integer port, int numThreads) {
        this.service = service;
        this.invoker = new MethodInvoker(service);
        try {
            this.serverSocket = new ServerSocket(port);
            this.serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.running = true;
        numThreads++; // plus one for server socket accepting thread
        this.executor = new ThreadPoolExecutor(numThreads, numThreads,
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.executor.execute(this); // server socket thread
    }

    int getPort() {
        return this.serverSocket.getLocalPort();
    }

    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                executor.execute(() -> handleConnection(socket));
            } catch (SocketException e) { // server socket is closed
                running = false;
            } catch (Exception e) {
                log.warn("Error processing connection", e);
            }
        }
    }

    private void handleConnection(Socket socket) {
        ClassLoader serviceCL = service.getClass().getClassLoader();
        try (Socket sock = socket;
             ObjectInputStream in = new BasicObjectInputStream(socket.getInputStream(), serviceCL);
             ObjectOutputStream out = new BasicObjectOutputStream(socket.getOutputStream())) {
            handleCall(in, out);
        } catch (SocketException se) {
            return; // e.g. connection closed by client
        } catch (Exception e) {
            log.warn("Error processing service call", e);
        }
    }

    private void handleCall(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        String methodName = (String)in.readObject();
        Object[] args = (Object[])in.readObject();
        Throwable error = null;
        Object result = null;
        try {
            result = resolveAsync(invoker.invoke(methodName, args));
        } catch (Throwable t) {
            error = t;
        }
        out.writeObject(error);
        out.writeObject(result);
    }

    @SuppressWarnings("unchecked")
    private Object resolveAsync(Object result) throws InterruptedException, Throwable {
        // exceptions are wrapped in an InvocationTargetException just like in a sync invoke
        if (result instanceof Future) {
            Future<Object> fu = (Future<Object>) result;
            try {
                result = fu.get();
            } catch (ExecutionException e) {
                throw new InvocationTargetException(e.getCause());
            }
        } else if (result instanceof CompletionStage) {
            CompletionStage<Object> fu = (CompletionStage<Object>) result;
            try {
                result = fu.toCompletableFuture().get();
            } catch (ExecutionException e) {
                throw new InvocationTargetException(e.getCause());
            }
        } else if (result instanceof Promise) {
            Promise<Object> fu = (Promise<Object>) result;
            try {
                result = fu.getValue();
            } catch (InvocationTargetException e) {
                throw e;
            }
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        this.serverSocket.close();
        this.running = false;
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        this.executor.shutdownNow();
    }

}
