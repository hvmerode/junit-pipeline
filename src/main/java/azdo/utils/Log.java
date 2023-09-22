package azdo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static azdo.utils.Constants.*;

@SuppressWarnings({"UnusedDeclaration"})
public class Log
{
    private static Log instance = null;
    private static Logger logger = null;

    private Log()
    {
        logger = LoggerFactory.getLogger(Log.class);
    }

    // Singleton with double-checked locking
//    public static Log getLogger() {
//        if (instance == null) {
//            synchronized (Log.class) {
//                if (instance == null) {
//                    instance = new Log();
//                }
//            }
//        }
//        return instance;
//    }

    // Not the fastest Singleton because of the synchronized in each getLogger() call, but runs fine
    // on all platforms (compared to a double-checked locking solution)
    public static synchronized Log getLogger() {
        if (instance == null)
            instance = new Log();
        return instance;
    }

    public void debug(Throwable t)
    {
        logger.debug(t.getMessage(), t);
    }

    public void info(Throwable t)
    {
        logger.info(t.getMessage(), t);
    }

    public void warn(Throwable t)
    {
        logger.warn(t.getMessage(), t);
    }

    public void error(Throwable t)
    {
        logger.error(LIGHT_RED + t.getMessage() + RESET_COLOR, t);
    }

    public void debug(String format, Object... args)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(format, args);
        }
    }

    public void info(String format, Object... args)
    {
        if (logger.isInfoEnabled()) {
            logger.info(LIGHT_GREEN + format + RESET_COLOR, args);
        }
    }

    public void infoColor(String color, String format, Object... args)
    {
        if (logger.isInfoEnabled()) {
            logger.info(color + format + RESET_COLOR, args);
        }
    }

    public void warn(String format, Object... args)
    {
        if (logger.isWarnEnabled()) {
            logger.warn(LIGHT_YELLOW + format + RESET_COLOR, args);
        }
    }

    public void error(String format, Object... args)
    {
        if (logger.isErrorEnabled()) {
            logger.error(LIGHT_RED + format + RESET_COLOR, args);
        }
    }

    public void debug(Throwable t, String format, Object... args)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(format, args, t);
        }
    }

    public void info(Throwable t, String format, Object... args)
    {
        if (logger.isInfoEnabled()) {
            logger.info(format, args, t);
        }
    }

    public void warn(Throwable t, String format, Object... args)
    {
        if (logger.isWarnEnabled()) {
            logger.warn(format, args, t);
        }
    }

    public void error(Throwable t, String format, Object... args)
    {
        if (logger.isErrorEnabled()) {
            logger.error(LIGHT_RED + format + RESET_COLOR, args, t);
        }
    }
}