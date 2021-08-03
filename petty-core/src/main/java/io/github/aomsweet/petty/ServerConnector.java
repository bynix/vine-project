package io.github.aomsweet.petty;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.proxy.ProxyHandler;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author aomsweet
 */
public interface ServerConnector {

    ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx);

    ChannelFuture channel(InetSocketAddress socketAddress, ChannelHandlerContext ctx, List<ProxyInfo> upstreamProxies);

}
