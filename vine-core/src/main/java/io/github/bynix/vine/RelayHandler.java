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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author bynix
 */
public abstract class RelayHandler extends ChannelInboundHandlerAdapter implements AutoCloseable {

    protected final InternalLogger logger;
    protected final VineServer vine;

    protected State state;
    protected Channel relayChannel;
    protected ChannelHandlerContext ctx;

    public RelayHandler(VineServer vine, InternalLogger logger) {
        this.vine = vine;
        this.logger = logger;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        super.handlerAdded(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("{} INACTIVE. CLOSING RELAY CHANNEL {}", ctx.channel(), relayChannel);
        }
        close();
    }

    public void relay(Object msg) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg);
        } else {
            close();
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean isWritable = ctx.channel().isWritable();
        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITABILITY CHANGED. CURRENT STATUS: {}. {} {}", ctx.channel(),
                isWritable ? "WRITABLE" : "NOT WRITABLE", relayChannel,
                isWritable ? "ENABLE AUTO READ" : "DISABLE AUTO READ");
        }
        relayChannel.config().setAutoRead(isWritable);
    }

    public final void close() {
        if (state != State.CLOSED) {
            release();
            releaseRelayChannel();
            if (logger.isDebugEnabled()) {
                logger.debug("Channel released. {}", ctx.channel());
            }
            state = State.CLOSED;
        }
    }

    protected void release() {
        ctx.close();
    }

    protected void releaseRelayChannel() {
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            if (cause instanceof IOException || cause instanceof RejectedExecutionException) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
                } else {
                    logger.info("{}: {}", cause.getClass().getName(), cause.getMessage());
                }
            } else {
                logger.error("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
            }
        } finally {
            close();
        }
    }

    public enum State {

        UNCONNECTED, CONNECTED, READY, CLOSED

    }

}
