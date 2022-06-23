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
package io.github.bynix.vine.app.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.layout.TTLLLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;

/**
 * @author bynix
 */
public class LogbackConfigurator extends ContextAwareBase implements Configurator {

    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public LogbackConfigurator() {
    }

    @Override
    public void configure(LoggerContext loggerContext) {
        AnsiConsoleAppender<ILoggingEvent> consoleAppender = new AnsiConsoleAppender<>();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName(AnsiConsoleAppender.DEFAULT_NAME);

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(loggerContext);

        TTLLLayout layout = new TTLLLayout();
        layout.setContext(loggerContext);
        layout.start();

        encoder.setLayout(layout);

        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.DEBUG);
        rootLogger.addAppender(consoleAppender);

        enabled = true;
    }
}
