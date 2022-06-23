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
package io.github.bynix.vine.http;

import io.github.bynix.vine.Credentials;
import io.github.bynix.vine.RelayHandler;
import io.github.bynix.vine.UpstreamProxy;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author bynix
 */
public interface HttpChannelContext extends AutoCloseable {

    HttpRequest getHttpRequest();

    UpstreamProxy getUpstreamProxy();

    HttpChannelContext setUpstreamProxy(UpstreamProxy upstreamProxy);

    Channel getClientChannel();

    Channel getServerChannel();

    <T extends SocketAddress> T getClientAddress();

    InetSocketAddress getServerAddress();

    Credentials getCredentials();

    RelayHandler.State getState();

    ChannelHandlerContext getChannelHandlerContext();

    void cancelRelay();

    boolean isSsl();

    <T> T getData();

    HttpChannelContext setData(Object data);
}
