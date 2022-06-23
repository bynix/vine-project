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
package io.github.bynix.vine.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.bynix.vine.Credentials;
import io.github.bynix.vine.VineServer;
import io.github.bynix.vine.UpstreamProxy;
import io.github.bynix.vine.UpstreamProxyManager;
import io.github.bynix.vine.app.logback.AnsiConsoleAppender;
import io.github.bynix.vine.app.logback.LogbackConfigurator;
import io.github.bynix.vine.http.HttpChannelContext;
import io.github.bynix.vine.http.interceptor.*;
import io.github.bynix.vine.http.mitm.BouncyCastleSelfSignedMitmManager;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author bynix
 */
public class VineLauncher {

    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final Logger logger = LoggerFactory.getLogger(VineLauncher.class);

    public static void main(String[] args) throws Exception {
        /*
         Register a signal handler for Ctrl-C that runs the shutdown hooks
         https://github.com/oracle/graal/issues/465
         */
        Signal.handle(new Signal("INT"), s -> System.exit(0));
        if (LogbackConfigurator.isEnabled()) {
            logbackConfigure();
        }
        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        logger.info("Starting Vine on {} ({})", mx.getName(), System.getProperty("user.dir"));
        VineServer vine = new VineServer.Builder()
            // .withProxyAuthenticator(((username, password) -> "admin".equals(username) && "admin".equals(password)))
            // .withUpstreamProxy(() -> new HttpProxyHandler(new InetSocketAddress("localhost", 7890)))
            // .withUpstreamProxy(ProxyType.SOCKS5, "127.0.0.1", 7890)
            .withUpstreamProxyManager(new UpstreamProxyManager() {
                @Override
                public Queue<? extends UpstreamProxy> lookupUpstreamProxies(Object requestObject, Credentials credentials, SocketAddress clientAddress, InetSocketAddress serverAddress) throws Exception {
                    Queue<UpstreamProxy> queue = new ArrayDeque<>(1);
                    queue.offer(new UpstreamProxy(UpstreamProxy.Protocol.HTTP, new InetSocketAddress("127.0.0.1", 7890)));
                    return queue;
                }

                @Override
                public void failConnectExceptionCaught(UpstreamProxy upstreamProxy, InetSocketAddress serverAddress, Throwable throwable) throws Exception {
                    throwable.printStackTrace();
                }
            })
            .withMitmManager(new BouncyCastleSelfSignedMitmManager())
            .withHttpInterceptorManager(new DefaultHttpInterceptorManager()
                .addInterceptor(new HttpInterceptor() {
                    @Override
                    public boolean match(HttpRequest httpRequest) {
                        return true;
                    }

                    @Override
                    public HttpRequestInterceptor requestInterceptor() {
                        return new FullHttpRequestInterceptor() {

                            @Override
                            public boolean preHandle(FullHttpRequest httpRequest, HttpChannelContext context) throws Exception {
                                httpRequest.headers().add("Vine", "for test.");
                                return true;
                            }
                        };
                    }

                    @Override
                    public HttpResponseInterceptor responseInterceptor() {
                        return new FullHttpResponseInterceptor() {

                            @Override
                            public boolean preHandle(HttpRequest httpRequest, FullHttpResponse httpResponse, HttpChannelContext context) throws Exception {
                                httpResponse.headers().add("Vine", "for test.");
                                return true;
                            }
                        };
                    }
                })
            )
            .withPort(2228)
            .build();
        vine.start().whenComplete((channel, cause) -> {
            if (channel == null) {
                vine.asyncStop(0).whenComplete((v, e) -> loggerContext.stop());
            } else {
                Thread shutdownHookThread = new Thread(() -> close(vine));
                shutdownHookThread.setName("Vine shutdown hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        });
    }

    public static void close(VineServer vine) {
        vine.close();
        loggerContext.stop();
    }

    public static void logbackConfigure() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        AnsiConsoleAppender<ILoggingEvent> consoleAppender = ((AnsiConsoleAppender<ILoggingEvent>) rootLogger
            .getAppender(AnsiConsoleAppender.DEFAULT_NAME));

        String pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS} | %highlight(%-5level) %green([%thread]) %boldMagenta(%logger{36}) - %cyan(%msg) %n%boldRed(%ex)";

        PatternLayout layout = new PatternLayout();
        layout.setPattern(pattern);
        layout.setContext(loggerContext);
        layout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = (LayoutWrappingEncoder<ILoggingEvent>) consoleAppender.getEncoder();
        encoder.setLayout(layout);

        consoleAppender.setWithJansi(true);
        consoleAppender.start();
    }

}
