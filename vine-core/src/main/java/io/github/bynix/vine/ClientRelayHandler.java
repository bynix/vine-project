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

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author bynix
 */
public abstract class ClientRelayHandler<T> extends RelayHandler {

    private Queue<Object> pendingWrites;

    protected final ChannelManager channelManager;
    protected final UpstreamProxyManager upstreamProxyManager;

    protected Channel clientChannel;
    protected Credentials credentials;
    protected UpstreamProxy upstreamProxy;
    protected InetSocketAddress serverAddress;

    public ClientRelayHandler(VineServer vine, InternalLogger logger) {
        super(vine, logger);
        this.channelManager = vine.channelManager;
        this.upstreamProxyManager = vine.upstreamProxyManager;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.clientChannel = ctx.channel();
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.READY) {
            relay(msg);
        } else {
            channelRead0(msg);
        }
    }

    public abstract void channelRead0(Object msg) throws Exception;

    protected void doConnectServer(T request) throws Exception {
        ChannelFuture future = acquireChannelFuture(request);
        future.addListener(action -> {
            try {
                if (action.isSuccess()) {
                    if (clientChannel.isActive()) {
                        state = State.CONNECTED;
                        relayChannel = future.channel();
                        dockingRelay();
                    } else {
                        future.channel().close().addListener(ChannelFutureListener.CLOSE);
                    }
                } else {
                    logger.error("Unable to establish a remote connection.", action.cause());
                    close();
                }
            } catch (Exception e) {
                logger.error("{}: {}", e.getClass().getName(), e.getMessage(), e);
                close();
            }
        });
    }

    private ChannelFuture acquireChannelFuture(T request) throws Exception {
        if (upstreamProxy == null) {
            if (upstreamProxyManager == null) {
                return channelManager.acquire(serverAddress, ctx);
            } else {
                Queue<? extends UpstreamProxy> upstreamProxies = upstreamProxyManager.lookupUpstreamProxies(request,
                    credentials, clientChannel.remoteAddress(), serverAddress);

                if (upstreamProxies == null || upstreamProxies.isEmpty()) {
                    return channelManager.acquire(serverAddress, ctx);
                } else {
                    CompleteChannelPromise promise = new CompleteChannelPromise(ctx.channel().eventLoop());
                    acquireChannelFuture(upstreamProxies, promise);
                    return promise;
                }
            }
        } else {
            return channelManager.acquire(serverAddress, upstreamProxy, ctx);
        }
    }

    private void acquireChannelFuture(Queue<? extends UpstreamProxy> upstreamProxies, CompleteChannelPromise promise) {
        UpstreamProxy upstreamProxy = upstreamProxies.poll();
        if (logger.isDebugEnabled()) {
            logger.debug("Use upstream proxy: [{}]", upstreamProxy);
        }
        channelManager.acquire(serverAddress, upstreamProxy, ctx).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                this.upstreamProxy = upstreamProxy;
                promise.setChannel(future.channel()).setSuccess();
            } else {
                Throwable cause = future.cause();
                upstreamProxyManager.failConnectExceptionCaught(upstreamProxy, serverAddress, cause);
                if (upstreamProxies.peek() != null) {
                    acquireChannelFuture(upstreamProxies, promise);
                } else {
                    promise.setFailure(cause);
                }
            }
        });
    }

    public void dockingRelay() throws Exception {
        if (relayChannel.isActive()) {
            relayChannel.pipeline().addLast(HandlerNames.RELAY, newServerRelayHandler());
            if (pendingWrites != null) {
                for (Object message = pendingWrites.poll(); message != null; message = pendingWrites.poll()) {
                    relayChannel.write(message);
                }
                relayChannel.flush();
                pendingWritesGC();
            }
            state = State.READY;
            if (!clientChannel.config().isAutoRead()) {
                clientChannel.config().setAutoRead(true);
            }
        } else {
            close();
        }
    }

    protected void addPendingWrites(Object msg) {
        if (pendingWrites == null) {
            pendingWrites = new ArrayDeque<>(3);
        }
        pendingWrites.offer(msg);
    }

    protected void pendingWritesGC() {
        pendingWrites = null;
    }

    public ChannelHandler newServerRelayHandler() throws Exception {
        return new ServerRelayHandler(vine, clientChannel);
    }

    @Override
    protected final void release() {
        if (pendingWrites != null) {
            for (Object message = pendingWrites.poll(); message != null; message = pendingWrites.poll()) {
                ReferenceCountUtil.release(message);
            }
            pendingWritesGC();
        }
        super.release();
    }

    @Override
    protected void releaseRelayChannel() {
        if (relayChannel == null || !relayChannel.isActive()) {
            return;
        }
        ChannelManager channelManager = vine.channelManager;
        if (channelManager != null) {
            if (upstreamProxy == null) {
                channelManager.release(relayChannel, serverAddress);
            } else {
                channelManager.release(relayChannel, serverAddress, upstreamProxy);
            }
        }
    }

    public ClientRelayHandler<T> setCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

}
