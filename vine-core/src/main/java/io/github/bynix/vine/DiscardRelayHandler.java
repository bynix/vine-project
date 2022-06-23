package io.github.bynix.vine;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * @author bynix
 */
@ChannelHandler.Sharable
public class DiscardRelayHandler extends ChannelInboundHandlerAdapter {

    public static final DiscardRelayHandler INSTANCE = new DiscardRelayHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ReferenceCountUtil.release(msg);
    }
}
