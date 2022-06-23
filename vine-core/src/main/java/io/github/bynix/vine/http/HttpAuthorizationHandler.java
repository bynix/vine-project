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
import io.github.bynix.vine.HandlerNames;
import io.github.bynix.vine.VineServer;
import io.github.bynix.vine.ProxyAuthenticator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Base64;

/**
 * @author bynix
 */
@ChannelHandler.Sharable
public class HttpAuthorizationHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpAuthorizationHandler.class);

    public static final byte[] UNAUTHORIZED_RESPONSE = "HTTP/1.1 407 Unauthorized\r\nProxy-Authenticate: Basic realm=\"Access to the staging site\"\r\n\r\n".getBytes();

    VineServer vine;

    public HttpAuthorizationHandler(VineServer vine) {
        this.vine = vine;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.decoderResult().isSuccess()) {
                ProxyAuthenticator authenticator = vine.getProxyAuthenticator();
                if (authenticator != null) {
                    Credentials credentials = resolveCredentials(httpRequest);
                    if (credentials == null || !authenticator.authenticate(credentials.getUsername(), credentials.getPassword())) {
                        ByteBuf byteBuf = ctx.alloc().buffer(UNAUTHORIZED_RESPONSE.length);
                        ctx.writeAndFlush(byteBuf.writeBytes(UNAUTHORIZED_RESPONSE))
                            .addListener(ChannelFutureListener.CLOSE);
                    } else {
                        switchClientRelayHandler(ctx, httpRequest, credentials);
                    }
                } else if (vine.getUpstreamProxyManager() != null || vine.getHttpInterceptorManager() != null) {
                    Credentials credentials = resolveCredentials(httpRequest);
                    switchClientRelayHandler(ctx, httpRequest, credentials);
                } else {
                    switchClientRelayHandler(ctx, httpRequest, null);
                }
            } else {
                ctx.close();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    public void switchClientRelayHandler(ChannelHandlerContext ctx, HttpRequest httpRequest, Credentials credentials) {
        ChannelHandler relayHandler;
        if (HttpMethod.CONNECT.equals(httpRequest.method())) {
            if (vine.getMitmManager() == null) {
                relayHandler = new HttpTunnelClientRelayHandler(vine).setCredentials(credentials);
            } else {
                relayHandler = new HttpsClientRelayHandler(vine).setCredentials(credentials);
            }
        } else {
            relayHandler = new HttpClientRelayHandler(vine).setCredentials(credentials);
        }
        ctx.pipeline().addLast(HandlerNames.RELAY, relayHandler);
        ctx.fireChannelRead(httpRequest).pipeline().remove(this);
    }

    protected Credentials resolveCredentials(HttpRequest request) {
        HttpHeaders headers = request.headers();
        String authorization = headers.get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (authorization == null || authorization.isEmpty()) {
            return null;
        } else {
            int i = authorization.indexOf(' ');
            String token = i > -1 && ++i < authorization.length()
                ? authorization.substring(i) : authorization;
            String decode = new String(Base64.getDecoder().decode(token));
            i = decode.indexOf(':');
            if (i > -1) {
                return new Credentials(decode.substring(0, i), decode.substring(++i));
            } else {
                return new Credentials(null, decode);
            }
        }
    }

}
