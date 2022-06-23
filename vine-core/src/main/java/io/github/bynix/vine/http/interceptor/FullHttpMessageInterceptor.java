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

/**
 * @author bynix
 */
public abstract class FullHttpMessageInterceptor<T extends FullHttpMessageInterceptor<T>> {

    protected static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 8;

    protected int maxContentLength;

    public FullHttpMessageInterceptor() {
        this(DEFAULT_MAX_CONTENT_LENGTH);
    }

    public FullHttpMessageInterceptor(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    @SuppressWarnings("unchecked")
    public T setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return (T) this;
    }
}
