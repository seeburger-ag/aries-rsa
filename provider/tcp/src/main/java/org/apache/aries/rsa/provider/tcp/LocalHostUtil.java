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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Utility methods to get the local address even on a linux host.
 */
public final class LocalHostUtil {

    private LocalHostUtil() {
        // Util Class
    }

    /**
     * Returns an InetAddress representing the address of the localhost. Every
     * attempt is made to find an address for this host that is not the loopback
     * address. If no other address can be found, the loopback will be returned.
     *
     * @return InetAddress the address of localhost
     * @throws UnknownHostException if there is a problem determining the address
     */
    public static InetAddress getLocalHost() throws UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        if (!localHost.isLoopbackAddress()) {
            return localHost;
        }
        InetAddress[] addrs = getAllLocalUsingNetworkInterface();
        for (InetAddress addr : addrs) {
            if (!addr.isLoopbackAddress() && !addr.getHostAddress().contains(":")) {
                return addr;
            }
        }
        return localHost;
    }

    /**
     * Utility method that delegates to the methods of NetworkInterface to
     * determine addresses for this machine.
     *
     * @return all addresses found from the NetworkInterfaces
     * @throws UnknownHostException if there is a problem determining addresses
     */
    private static InetAddress[] getAllLocalUsingNetworkInterface() throws UnknownHostException {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                for (Enumeration<InetAddress> e2 = ni.getInetAddresses(); e2.hasMoreElements();) {
                    addresses.add(e2.nextElement());
                }
            }
            return addresses.toArray(new InetAddress[] {});
        } catch (SocketException ex) {
            throw new UnknownHostException("127.0.0.1");
        }
    }

    public static String getLocalIp() {
        String localIP;
        try {
            localIP = getLocalHost().getHostAddress();
        } catch (Exception e) {
            localIP = "localhost";
        }
        return localIP;
    }
}
