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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author bynix
 */
public class ServerRelayHandler extends RelayHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServerRelayHandler.class);

    public ServerRelayHandler(VineServer vine, Channel relayChannel) {
        super(vine, logger);
        this.relayChannel = relayChannel;
        this.state = State.READY;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        relay(msg);
    }
}
