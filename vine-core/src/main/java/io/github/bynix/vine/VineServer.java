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

import io.github.bynix.vine.http.interceptor.HttpInterceptorManager;
import io.github.bynix.vine.http.mitm.MitmManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author bynix
 */
public class VineServer implements Closeable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(VineServer.class);

    HttpInterceptorManager httpInterceptorManager;
    UpstreamProxyManager upstreamProxyManager;
    MitmManager mitmManager;
    SslContext clientSslContext;
    ChannelManager channelManager;
    ProxyAuthenticator proxyAuthenticator;

    SocketAddress actualBoundAddress;
    SocketAddress preBoundAddress;

    int acceptorEventLoopGroupSize;
    boolean holdAcceptorEventLoopGroup;
    EventLoopGroup acceptorEventLoopGroup;
    int workerEventLoopGroupSize;
    boolean holdWorkerEventLoopGroup;
    EventLoopGroup workerEventLoopGroup;

    private VineServer() {
    }

    public CompletionStage<Channel> start() {
        if (acceptorEventLoopGroup == null) {
            holdAcceptorEventLoopGroup = true;
            acceptorEventLoopGroup = new NioEventLoopGroup(acceptorEventLoopGroupSize,
                threadFactory("Vine acceptor-"));
        }
        if (workerEventLoopGroup == null) {
            holdWorkerEventLoopGroup = true;
            workerEventLoopGroup = new NioEventLoopGroup(workerEventLoopGroupSize,
                threadFactory("Vine worker-"));
        }
        return doBind();
    }

    private CompletionStage<Channel> doBind() {
        final long startTimestamp = System.currentTimeMillis();
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(acceptorEventLoopGroup, workerEventLoopGroup)
            .channel(NioServerSocketChannel.class);
        if (logger.isTraceEnabled()) {
            bootstrap.handler(new LoggingHandler(LogLevel.TRACE));
        }
        PortUnificationServerHandler unificationServerHandler = new PortUnificationServerHandler(this);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (logger.isTraceEnabled()) {
                    pipeline.addLast(HandlerNames.LOGGING, new LoggingHandler(LogLevel.TRACE));
                }
                pipeline.addLast(HandlerNames.ROOT, unificationServerHandler);
            }
        });
        CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
        bootstrap.bind(preBoundAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                channelFuture.complete(channel);
                actualBoundAddress = channel.localAddress();
                String address = actualBoundAddress.toString();
                if (address.charAt(0) == '/') {
                    address = address.substring(1);
                }
                logger.info("Vine started in {}s. Listening on: {}",
                    (System.currentTimeMillis() - startTimestamp) / 1000.0, address);
            } else {
                logger.error("Vine start failed.", future.cause());
                channelFuture.completeExceptionally(future.cause());
            }
        });
        return channelFuture;
    }

    public ThreadFactory threadFactory(String prefix) {
        AtomicInteger threadSequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + threadSequence.getAndAdd(1));
            return thread;
        };
    }

    public CompletableFuture<Void> stop() throws InterruptedException, ExecutionException {
        return stop(30);
    }

    public CompletableFuture<Void> stop(int timeout) throws InterruptedException, ExecutionException {
        CompletableFuture<Void> future = doStop(timeout);
        future.get();
        return future;
    }

    public CompletableFuture<Void> asyncStop() {
        return asyncStop(30);
    }

    public CompletableFuture<Void> asyncStop(int timeout) {
        return doStop(timeout);
    }

    private CompletableFuture<Void> doStop(int timeout) {
        logger.info("Vine is stopping...");
        long stopTimestamp = System.currentTimeMillis();
        CompletableFuture<Void> future;
        if (holdAcceptorEventLoopGroup && !(acceptorEventLoopGroup.isShutdown() || acceptorEventLoopGroup.isShuttingDown())) {
            future = shutdownEventLoopGroup(acceptorEventLoopGroup, timeout,
                "Acceptor EventLoopGroup stopped.");
        } else {
            future = new CompletableFuture<>();
            future.complete(null);
        }
        if (holdWorkerEventLoopGroup && !(workerEventLoopGroup.isShutdown() || workerEventLoopGroup.isShuttingDown())) {
            future = future.thenCompose(unused -> shutdownEventLoopGroup(workerEventLoopGroup, timeout,
                "Worker EventLoopGroup stopped."));
        }
        future.whenComplete((v, e) -> {
            if (e == null) {
                logger.info("Vine stopped in {}s.", (System.currentTimeMillis() - stopTimestamp) / 1000.0);
            } else {
                logger.error("Failed to stop vine.", e);
            }
        });
        return future;
    }

    private CompletableFuture<Void> shutdownEventLoopGroup(EventLoopGroup eventLoopGroup, int timeout, String comment) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        eventLoopGroup.shutdownGracefully(0, timeout, TimeUnit.SECONDS).addListener(future -> {
            if (future.isSuccess()) {
                logger.info(comment);
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
    }

    @Override
    public void close() {
        try {
            stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    #####################################################################################
    ################################## Getter | Setter ##################################
    #####################################################################################
     */

    public HttpInterceptorManager getHttpInterceptorManager() {
        return httpInterceptorManager;
    }

    public VineServer setHttpInterceptorManager(HttpInterceptorManager httpInterceptorManager) {
        this.httpInterceptorManager = httpInterceptorManager;
        return this;
    }

    public UpstreamProxyManager getUpstreamProxyManager() {
        return upstreamProxyManager;
    }

    public VineServer setUpstreamProxyManager(UpstreamProxyManager upstreamProxyManager) {
        this.upstreamProxyManager = upstreamProxyManager;
        return this;
    }

    public MitmManager getMitmManager() {
        return mitmManager;
    }

    public VineServer setMitmManager(MitmManager mitmManager) {
        this.mitmManager = mitmManager;
        return this;
    }

    public SslContext getClientSslContext() {
        return clientSslContext;
    }

    public VineServer setClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
        return this;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public VineServer setChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
        return this;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public VineServer setProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        return this;
    }

    public SocketAddress getActualBoundAddress() {
        return actualBoundAddress;
    }

    public VineServer setActualBoundAddress(SocketAddress actualBoundAddress) {
        this.actualBoundAddress = actualBoundAddress;
        return this;
    }

    public SocketAddress getPreBoundAddress() {
        return preBoundAddress;
    }

    public VineServer setPreBoundAddress(SocketAddress preBoundAddress) {
        this.preBoundAddress = preBoundAddress;
        return this;
    }

    public int getAcceptorEventLoopGroupSize() {
        return acceptorEventLoopGroupSize;
    }

    public VineServer setAcceptorEventLoopGroupSize(int acceptorEventLoopGroupSize) {
        this.acceptorEventLoopGroupSize = acceptorEventLoopGroupSize;
        return this;
    }

    public boolean isHoldAcceptorEventLoopGroup() {
        return holdAcceptorEventLoopGroup;
    }

    public VineServer setHoldAcceptorEventLoopGroup(boolean holdAcceptorEventLoopGroup) {
        this.holdAcceptorEventLoopGroup = holdAcceptorEventLoopGroup;
        return this;
    }

    public EventLoopGroup getAcceptorEventLoopGroup() {
        return acceptorEventLoopGroup;
    }

    public VineServer setAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
        this.acceptorEventLoopGroup = acceptorEventLoopGroup;
        return this;
    }

    public int getWorkerEventLoopGroupSize() {
        return workerEventLoopGroupSize;
    }

    public VineServer setWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
        this.workerEventLoopGroupSize = workerEventLoopGroupSize;
        return this;
    }

    public boolean isHoldWorkerEventLoopGroup() {
        return holdWorkerEventLoopGroup;
    }

    public VineServer setHoldWorkerEventLoopGroup(boolean holdWorkerEventLoopGroup) {
        this.holdWorkerEventLoopGroup = holdWorkerEventLoopGroup;
        return this;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public VineServer setWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
        this.workerEventLoopGroup = workerEventLoopGroup;
        return this;
    }

    /**
     * Builder
     */
    public static class Builder {

        VineServer vine;

        public Builder() {
            vine = new VineServer();
        }

        public VineServer build() {
            if (vine.getAcceptorEventLoopGroup() == null) {
                vine.acceptorEventLoopGroupSize = 1;
            }
            if (vine.getWorkerEventLoopGroup() == null) {
                vine.workerEventLoopGroupSize = Runtime.getRuntime().availableProcessors();
            }
            if (vine.channelManager == null) {
                vine.channelManager = new UnpooledChannelManager();
            }
            if (vine.preBoundAddress == null) {
                vine.preBoundAddress = new InetSocketAddress("127.0.0.1", 2228);
            }
            return vine;
        }

        public Builder withHttpInterceptorManager(HttpInterceptorManager httpInterceptorManager) {
            vine.httpInterceptorManager = httpInterceptorManager;
            return this;
        }

        public Builder withUpstreamProxyManager(UpstreamProxyManager upstreamProxyManager) {
            vine.upstreamProxyManager = upstreamProxyManager;
            return this;
        }

        public Builder withMitmManager(MitmManager mitmManager) {
            vine.mitmManager = mitmManager;
            return this;
        }

        public Builder withClientSslContext(SslContext clientSslContext) {
            vine.clientSslContext = clientSslContext;
            return this;
        }

        public Builder withChannelManager(ChannelManager channelManager) {
            vine.channelManager = channelManager;
            return this;
        }

        public Builder withProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
            vine.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        public Builder withPort(int port) {
            vine.preBoundAddress = new InetSocketAddress(port);
            return this;
        }

        public Builder withAddress(String host, int port) {
            vine.preBoundAddress = new InetSocketAddress(host, port);
            return this;
        }

        public Builder withAddress(SocketAddress address) {
            vine.preBoundAddress = address;
            return this;
        }

        public Builder withBossEventLoopGroupSize(int bossEventLoopGroupSize) {
            vine.acceptorEventLoopGroupSize = bossEventLoopGroupSize;
            return this;
        }

        public Builder withAcceptorEventLoopGroup(EventLoopGroup acceptorEventLoopGroup) {
            vine.acceptorEventLoopGroup = acceptorEventLoopGroup;
            return this;
        }

        public Builder withWorkerEventLoopGroupSize(int workerEventLoopGroupSize) {
            vine.workerEventLoopGroupSize = workerEventLoopGroupSize;
            return this;
        }

        public Builder withWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
            vine.workerEventLoopGroup = workerEventLoopGroup;
            return this;
        }

    }
}
