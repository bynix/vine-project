package io.github.bynix.vine.http.interceptor;

import io.netty.handler.codec.http.HttpRequest;

/**
 * @author bynix
 */
public interface HttpInterceptor {

    boolean match(HttpRequest httpRequest);

    default HttpRequestInterceptor requestInterceptor() {
        return null;
    }

    default HttpResponseInterceptor responseInterceptor() {
        return null;
    }
}
