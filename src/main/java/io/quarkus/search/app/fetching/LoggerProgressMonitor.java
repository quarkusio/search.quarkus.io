package io.quarkus.search.app.fetching;

import java.time.Duration;

import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.jboss.logging.Logger;

/**
 * An improved version of {@link org.eclipse.jgit.lib.TextProgressMonitor}
 * that sends progress to a logger instead of stderr,
 * and prefixes all messages with a given string.
 */
public class LoggerProgressMonitor extends BatchingProgressMonitor {

    public static ProgressMonitor create(Logger logger, String prefix) {
        if (!logger.isInfoEnabled() && !logger.isDebugEnabled() && !logger.isTraceEnabled()) {
            return NullProgressMonitor.INSTANCE;
        }
        return new LoggerProgressMonitor(logger, prefix);
    }

    private final Logger logger;
    private final String prefix;

    private LoggerProgressMonitor(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void onUpdate(String taskName, int workCurr, Duration duration) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr, duration);
        // See superclass, this is only called once per second
        send(Logger.Level.INFO, s);
    }

    @Override
    protected void onEndTask(String taskName, int workCurr, Duration duration) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr, duration);
        s.append("\n");
        send(Logger.Level.INFO, s);
    }

    private void format(StringBuilder s, String taskName, int workCurr,
            Duration duration) {
        s.append(prefix);
        s.append(taskName);
        s.append(": ");
        while (s.length() < (prefix.length() + 25))
            s.append(' ');
        s.append(workCurr);
        appendDuration(s, duration);
    }

    @Override
    protected void onUpdate(String taskName, int cmp, int totalWork, int pcnt,
            Duration duration) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, cmp, totalWork, pcnt, duration);
        send(logLevelForUpdate(pcnt), s);
    }

    @Override
    protected void onEndTask(String taskName, int cmp, int totalWork, int pcnt,
            Duration duration) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, cmp, totalWork, pcnt, duration);
        s.append("\n"); //$NON-NLS-1$
        send(Logger.Level.INFO, s);
    }

    private void format(StringBuilder s, String taskName, int cmp,
            int totalWork, int pcnt, Duration duration) {
        s.append(prefix);
        s.append(taskName);
        s.append(": ");
        while (s.length() < (prefix.length() + 25))
            s.append(' ');

        String endStr = String.valueOf(totalWork);
        String curStr = String.valueOf(cmp);
        while (curStr.length() < endStr.length())
            curStr = " " + curStr;
        if (pcnt < 100)
            s.append(' ');
        if (pcnt < 10)
            s.append(' ');
        s.append(pcnt);
        s.append("% (");
        s.append(curStr);
        s.append('/');
        s.append(endStr);
        s.append(')');
        appendDuration(s, duration);
    }

    private Logger.Level logLevelForUpdate(int percent) {
        if (percent % 20 == 0 || percent % 50 == 0) {
            return Logger.Level.INFO;
        }
        if (percent % 5 == 0) {
            return Logger.Level.DEBUG;
        }
        return Logger.Level.TRACE;
    }

    private void send(Logger.Level level, StringBuilder s) {
        logger.log(level, s);
    }

}
