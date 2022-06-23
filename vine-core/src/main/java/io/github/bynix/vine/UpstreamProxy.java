/*
  Copyright 2021 The Vine Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.bynix.vine;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

/**
 * @author bynix
 */
public class UpstreamProxy {

    protected Protocol protocol;
    protected InetSocketAddress socketAddress;
    protected String username;
    protected String password;

    public UpstreamProxy(Protocol protocol, InetSocketAddress socketAddress) {
        this.protocol = protocol;
        this.socketAddress = socketAddress;
    }

    public UpstreamProxy(Protocol protocol, InetSocketAddress socketAddress, String username, String password) {
        this.protocol = protocol;
        this.socketAddress = socketAddress;
        this.username = username;
        this.password = password;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public UpstreamProxy setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }

    public UpstreamProxy setSocketAddress(InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public UpstreamProxy setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public UpstreamProxy setPassword(String password) {
        this.password = password;
        return this;
    }

    public ChannelHandler newProxyHandler() {
        return protocol.newProxyHandler(this);
    }

    @Override
    public String toString() {
        String host = socketAddress.getHostString();
        int port = socketAddress.getPort();
        if (username == null && password == null) {
            return protocol.name() + "://" + host + ':' + port;
        }
        return protocol.name() + "://" +
            (username == null ? "" : username) + ':' +
            (password == null ? "" : password) + '@' + host + ':' + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpstreamProxy that = (UpstreamProxy) o;

        if (!protocol.equals(that.protocol)) return false;
        return socketAddress.equals(that.socketAddress);
    }

    @Override
    public int hashCode() {
        int result = protocol.hashCode();
        result = 31 * result + socketAddress.hashCode();
        return result;
    }

    public interface Protocol {

        Protocol HTTP = new Protocol() {
            @Override
            public String name() {
                return "http";
            }

            @Override
            public ChannelHandler newProxyHandler(UpstreamProxy upstreamProxy) {
                if (upstreamProxy.username == null || upstreamProxy.password == null) {
                    return new HttpProxyHandler(upstreamProxy.socketAddress);
                } else {
                    return new HttpProxyHandler(upstreamProxy.socketAddress, upstreamProxy.username, upstreamProxy.password);
                }
            }
        };

        Protocol SOCKS4A = new Protocol() {
            @Override
            public String name() {
                return "socks4a";
            }

            @Override
            public ChannelHandler newProxyHandler(UpstreamProxy upstreamProxy) {
                return new Socks4ProxyHandler(upstreamProxy.socketAddress);
            }
        };

        Protocol SOCKS5 = new Protocol() {
            @Override
            public String name() {
                return "socks5";
            }

            @Override
            public ChannelHandler newProxyHandler(UpstreamProxy upstreamProxy) {
                return new Socks5ProxyHandler(upstreamProxy.socketAddress, upstreamProxy.username, upstreamProxy.password);
            }
        };

        String name();

        ChannelHandler newProxyHandler(UpstreamProxy upstreamProxy);
    }
}
