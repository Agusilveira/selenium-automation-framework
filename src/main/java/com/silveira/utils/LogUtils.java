package com.silveira.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Fachada de logging.
 *
 * Resuelve el logger por la clase que llama, así el log dice de dónde salió cada
 * línea sin que cada clase tenga que declarar su propio logger. Los loggers se
 * cachean porque resolver la clase llamadora en cada línea es caro.
 */
public final class LogUtils {

    private static final Map<String, Logger> CACHE = new ConcurrentHashMap<>();

    private LogUtils() {
    }

    private static Logger logger() {
        String clase = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(f -> !f.getClassName().equals(LogUtils.class.getName()))
                        .findFirst()
                        .map(StackWalker.StackFrame::getClassName)
                        .orElse(LogUtils.class.getName()));
        return CACHE.computeIfAbsent(clase, LogManager::getLogger);
    }

    public static void info(String mensaje)  { logger().info(mensaje); }
    public static void warn(String mensaje)  { logger().warn(mensaje); }
    public static void error(String mensaje) { logger().error(mensaje); }
    public static void debug(String mensaje) { logger().debug(mensaje); }

    public static void error(String mensaje, Throwable causa) {
        logger().error(mensaje, causa);
    }
}
