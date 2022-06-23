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
package io.github.bynix.vine.http.interceptor;

import io.github.bynix.vine.HandlerNames;
import io.github.bynix.vine.http.HttpChannelContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author bynix
 */
public abstract class FullHttpRequestInterceptor extends FullHttpMessageInterceptor<FullHttpRequestInterceptor>
    implements HttpRequestInterceptor {

    public FullHttpRequestInterceptor() {
        super();
    }

    public FullHttpRequestInterceptor(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    public final boolean preHandle(HttpRequest httpRequest, HttpChannelContext context) throws Exception {
        ChannelPipeline pipeline = context.getClientChannel().pipeline();
        if (httpRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequest;
            pipeline.remove(HandlerNames.DECOMPRESS);
            pipeline.remove(HandlerNames.AGGREGATOR);
            return preHandle(fullHttpRequest, context);
        } else {
            pipeline
                .addAfter(HandlerNames.DECODER, HandlerNames.DECOMPRESS, new HttpContentDecompressor())
                .addAfter(HandlerNames.DECOMPRESS, HandlerNames.AGGREGATOR, new HttpObjectAggregator(maxContentLength))
                .fireChannelRead(httpRequest);
            return false;
        }
    }

    public abstract boolean preHandle(FullHttpRequest httpRequest, HttpChannelContext context) throws Exception;

}
