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

import io.github.bynix.vine.VineServer;
import io.github.bynix.vine.HandlerNames;
import io.github.bynix.vine.ResolveServerAddressException;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author bynix
 */
public class HttpClientRelayHandler extends HttpBaseClientRelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(HttpClientRelayHandler.class);

    public HttpClientRelayHandler(VineServer vine) {
        super(vine, logger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead0(msg);
    }

    @Override
    public void handleHttpRequest(HttpRequest request) throws Exception {
        InetSocketAddress targetAddress = resolveServerAddress(request);
        if (targetAddress.equals(this.serverAddress)) {
            relay(request);
        } else {
            this.serverAddress = targetAddress;
            if (state == State.READY) {
                relayChannel.pipeline().remove(HandlerNames.RELAY);
                relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                state = State.UNCONNECTED;
            }

            addPendingWrites(request);
            doConnectServer(request);
        }
    }

    @Override
    public void handleHttpContent(HttpContent httpContent) {
        if (state == State.READY) {
            relay(httpContent);
        } else {
            addPendingWrites(httpContent);
        }
    }

    @Override
    public InetSocketAddress resolveServerAddress(HttpRequest httpRequest) throws ResolveServerAddressException {
        try {
            String uri = httpRequest.uri();
            String host;
            if (uri.charAt(0) == '/') {
                host = httpRequest.headers().get(HttpHeaderNames.HOST);
            } else {
                int index = uri.indexOf(':');
                char c = uri.charAt(index - 1);
                if (c == 's' || c == 'S') {
                    isSsl = true;
                }
                index = index + 3;
                int diag = uri.indexOf('/', index);
                host = diag == -1 ? uri.substring(index) : uri.substring(index, diag);
            }
            return resolveServerAddress(host, isSsl ? 443 : 80);
        } catch (Exception e) {
            throw new ResolveServerAddressException(getHttpRequestInitialLine(httpRequest), e);
        }
    }

    private InetSocketAddress resolveServerAddress(String host, int defaultPort) {
        int index = host.indexOf(':');
        if (index == -1) {
            return InetSocketAddress.createUnresolved(host, defaultPort);
        } else {
            return InetSocketAddress.createUnresolved(host.substring(0, index),
                Integer.parseInt(host.substring(index + 1)));
        }
    }
}
