package com.bracits.transactionservice.application.fakes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Captures the log events of one class's logger (Logback) for the duration of a test.
 */
public final class LogCapture implements AutoCloseable {

  private final Logger logger;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private LogCapture(Class<?> type) {
    this.logger = (Logger) LoggerFactory.getLogger(type);
    appender.start();
    logger.addAppender(appender);
  }

  public static LogCapture of(Class<?> type) {
    return new LogCapture(type);
  }

  /**
   * Formatted messages logged at {@code level}.
   */
  public List<String> messages(Level level) {
    return appender.list.stream()
        .filter(e -> e.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  /**
   * Raw message templates logged at {@code level}.
   */
  public List<String> templates(Level level) {
    return appender.list.stream()
        .filter(e -> e.getLevel() == level)
        .map(ILoggingEvent::getMessage)
        .toList();
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
