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

import io.github.bynix.vine.*;
import io.github.bynix.vine.*;
import io.github.bynix.vine.http.interceptor.HttpInterceptor;
import io.github.bynix.vine.http.interceptor.HttpInterceptorManager;
import io.github.bynix.vine.http.interceptor.HttpRequestInterceptor;
import io.github.bynix.vine.http.interceptor.HttpResponseInterceptor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.logging.InternalLogger;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author bynix
 */
public abstract class HttpBaseClientRelayHandler extends ClientRelayHandler<HttpRequest> implements HttpChannelContext {

    public static final byte[] TUNNEL_ESTABLISHED_RESPONSE = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();

    private Object data;

    protected boolean isSsl;
    protected HttpRequest httpRequest;
    protected Queue<HttpInterceptor> httpInterceptors;
    protected Queue<HttpResponseInterceptor> responseInterceptors;

    public HttpBaseClientRelayHandler(VineServer vine, InternalLogger logger) {
        super(vine, logger);
    }

    @Override
    public void channelRead0(Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.decoderResult().isSuccess()) {
                if (!preHandle(httpRequest)) {
                    return;
                }
                handleHttpRequest(httpRequest);
            } else {
                close();
            }
        } else if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            if (httpContent.decoderResult().isSuccess()) {
                handleHttpContent(httpContent);
            } else {
                close();
            }
        } else {
            handleUnknownMessage(msg);
        }
    }

    private boolean preHandle(HttpRequest httpRequest) throws Exception {
        if (httpInterceptors == null) {
            HttpInterceptorManager interceptorManager = vine.getHttpInterceptorManager();
            if (interceptorManager != null) {
                this.httpInterceptors = interceptorManager.matchInterceptor(httpRequest);
            }
        }
        if (httpInterceptors != null) {
            this.httpRequest = httpRequest;
            for (HttpInterceptor interceptor = httpInterceptors.peek();
                 interceptor != null; interceptor = httpInterceptors.peek()) {

                HttpRequestInterceptor requestInterceptor = interceptor.requestInterceptor();
                if (!(requestInterceptor == null || requestInterceptor.preHandle(httpRequest, this))) {
                    return false;
                } else if (httpRequest.method() != HttpMethod.CONNECT) {
                    HttpResponseInterceptor responseInterceptor = interceptor.responseInterceptor();
                    if (responseInterceptor != null) {
                        if (responseInterceptors == null) {
                            responseInterceptors = new ArrayDeque<>(httpInterceptors.size());
                        }
                        responseInterceptors.offer(responseInterceptor);
                    }
                }
                httpInterceptors.poll();
            }

            if (responseInterceptors == null) {
                this.httpRequest = null;
            }
            this.httpInterceptors = null;
        }

        return true;
    }

    public abstract void handleHttpRequest(HttpRequest httpRequest) throws Exception;

    public abstract void handleHttpContent(HttpContent httpContent) throws Exception;

    public void handleUnknownMessage(Object message) throws Exception {
        ctx.fireChannelRead(message);
    }

    @Override
    public ChannelHandler newServerRelayHandler() throws Exception {
        ChannelPipeline pipeline = relayChannel.pipeline();
        if (isSsl) {
            SslContext clientSslContext = getClientSslContext();
            pipeline.addLast(HandlerNames.SSL, clientSslContext.newHandler(relayChannel.alloc(),
                serverAddress.getHostName(), serverAddress.getPort()));
        }
        pipeline.addLast(HandlerNames.REQUEST_ENCODER, new HttpRequestEncoder());

        if (vine.getHttpInterceptorManager() == null) {
            return new ServerRelayHandler(vine, clientChannel);
        } else {
            return newInterceptedServerRelayHandler();
        }
    }

    protected ChannelHandler newInterceptedServerRelayHandler() {
        Channel serverChannel = relayChannel;
        ChannelPipeline clientPipeline = clientChannel.pipeline();
        ChannelPipeline serverPipeline = serverChannel.pipeline();

        serverPipeline.addLast(HandlerNames.DECODER, new HttpResponseDecoder());
        if (clientPipeline.get(HandlerNames.RESPONSE_ENCODER) == null) {
            clientPipeline.addLast(HandlerNames.RESPONSE_ENCODER, new HttpResponseEncoder());
        }

        return new ServerRelayHandler(vine, clientChannel) {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (responseInterceptors == null) {
                    super.channelRead(ctx, msg);
                } else if (msg instanceof HttpResponse) {
                    HttpResponse httpResponse = (HttpResponse) msg;
                    for (HttpResponseInterceptor interceptor = responseInterceptors.peek(); interceptor != null; interceptor = responseInterceptors.peek()) {
                        if (interceptor.preHandle(httpRequest, httpResponse, HttpBaseClientRelayHandler.this)) {
                            responseInterceptors.poll();
                        } else {
                            return;
                        }
                    }
                    httpRequest = null;
                    responseInterceptors = null;
                    super.channelRead(ctx, msg);
                } else {
                    super.channelRead(ctx, msg);
                }
            }
        };
    }

    public SslContext getClientSslContext() throws SSLException {
        SslContext clientSslContext;
        if ((clientSslContext = vine.getClientSslContext()) == null) {
            synchronized (vine) {
                if ((clientSslContext = vine.getClientSslContext()) == null) {
                    //https://github.com/GlowstoneMC/Glowstone/blob/5b89f945b4/src/main/java/net/glowstone/net/http/HttpClient.java
                    clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                    vine.setClientSslContext(clientSslContext);
                }
            }
        }
        return clientSslContext;
    }

    public InetSocketAddress resolveServerAddress(HttpRequest httpRequest) throws ResolveServerAddressException {
        try {
            String uri = httpRequest.uri();
            int index = uri.indexOf(':');
            return InetSocketAddress.createUnresolved(uri.substring(0, index), Integer.parseInt(uri.substring(index + 1)));
        } catch (Exception e) {
            throw new ResolveServerAddressException(getHttpRequestInitialLine(httpRequest), e);
        }
    }

    public String getHttpRequestInitialLine(HttpRequest httpRequest) {
        return httpRequest.method().name() + ' ' + httpRequest.uri() + ' ' + httpRequest.protocolVersion();
    }

    @Override
    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public UpstreamProxy getUpstreamProxy() {
        return upstreamProxy;
    }

    @Override
    public HttpChannelContext setUpstreamProxy(UpstreamProxy upstreamProxy) {
        this.upstreamProxy = upstreamProxy;
        return this;
    }

    @Override
    public Channel getClientChannel() {
        return clientChannel;
    }

    @Override
    public Channel getServerChannel() {
        return relayChannel;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends SocketAddress> T getClientAddress() {
        return (T) clientChannel.remoteAddress();
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    @Override
    public void cancelRelay() {
        if (relayChannel == null) {
            return;
        }
        state = State.UNCONNECTED;
        ChannelPipeline pipeline = relayChannel.pipeline();
        pipeline.replace(HandlerNames.RELAY, HandlerNames.RELAY, DiscardRelayHandler.INSTANCE);
        releaseRelayChannel();
    }

    @Override
    public boolean isSsl() {
        return isSsl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }

    @Override
    public HttpChannelContext setData(Object data) {
        this.data = data;
        return this;
    }
}
