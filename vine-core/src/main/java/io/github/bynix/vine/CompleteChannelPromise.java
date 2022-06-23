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
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * @author bynix
 */
public class CompleteChannelPromise extends DefaultPromise<Void> implements ChannelPromise {

    Channel channel;

    public CompleteChannelPromise(EventExecutor executor) {
        super(executor);
    }

    public CompleteChannelPromise setChannel(Channel channel) {
        this.channel = channel;
        return this;
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public CompleteChannelPromise setSuccess() {
        return setSuccess(null);
    }

    @Override
    public CompleteChannelPromise setSuccess(Void result) {
        super.setSuccess(result);
        return this;
    }

    @Override
    public boolean trySuccess() {
        return trySuccess(null);
    }

    @Override
    public CompleteChannelPromise setFailure(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    @Override
    public CompleteChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public CompleteChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public CompleteChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public CompleteChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public CompleteChannelPromise sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public CompleteChannelPromise syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    public CompleteChannelPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public CompleteChannelPromise awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    protected void checkDeadLock() {
        if (channel().isRegistered()) {
            super.checkDeadLock();
        }
    }

    @Override
    public CompleteChannelPromise unvoid() {
        return this;
    }

    @Override
    public boolean isVoid() {
        return false;
    }
}
