package ca.bc.gov.nrs.fta.batch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Collects what a class logs at ERROR, for asserting on batch error lines. */
public final class LogCapture implements AutoCloseable {

  private final Logger logger;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  public LogCapture(Class<?> type) {
    this.logger = (Logger) LoggerFactory.getLogger(type);
    appender.start();
    logger.addAppender(appender);
  }

  /** The formatted ERROR lines so far. */
  public List<String> errors() {
    return appender.list.stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  /** The ERROR events so far (to check the attached exception). */
  public List<ILoggingEvent> errorEvents() {
    return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
