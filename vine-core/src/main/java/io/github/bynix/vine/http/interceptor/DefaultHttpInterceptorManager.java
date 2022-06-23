package io.github.bynix.vine.http.interceptor;

import io.netty.handler.codec.http.HttpRequest;

import java.util.*;

/**
 * @author bynix
 */
public class DefaultHttpInterceptorManager implements HttpInterceptorManager {

    List<HttpInterceptor> httpInterceptors;

    public HttpInterceptorManager addInterceptor(HttpInterceptor interceptor) {
        Objects.requireNonNull(interceptor);
        if (httpInterceptors == null) {
            httpInterceptors = new ArrayList<>();
        }
        httpInterceptors.add(interceptor);
        return this;
    }

    public Queue<HttpInterceptor> matchInterceptor(HttpRequest httpRequest) {
        if (httpInterceptors == null) {
            return null;
        }
        Queue<HttpInterceptor> queue = null;
        for (HttpInterceptor httpInterceptor : httpInterceptors) {
            if (httpInterceptor.match(httpRequest)) {
                if (queue == null) {
                    queue = new ArrayDeque<>(2);
                }
                queue.offer(httpInterceptor);
            }
        }
        return queue;
    }

}
