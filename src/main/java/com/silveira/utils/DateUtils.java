package com.silveira.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Fechas y timestamps, con los formatos que usan reportes y nombres de archivo. */
public final class DateUtils {

    public static final String FORMATO_FECHA = "dd/MM/yyyy";
    public static final String FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm:ss";
    private static final DateTimeFormatter PARA_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private DateUtils() {
    }

    /** Seguro para nombres de archivo: sin barras ni dos puntos. */
    public static String timestampParaArchivo() {
        return LocalDateTime.now().format(PARA_ARCHIVO);
    }

    public static String hoy() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(FORMATO_FECHA));
    }

    public static String ahora() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(FORMATO_FECHA_HORA));
    }

    public static String formatear(LocalDate fecha, String patron) {
        return fecha.format(DateTimeFormatter.ofPattern(patron));
    }

    public static String sumarDias(long dias, String patron) {
        return LocalDate.now().plusDays(dias).format(DateTimeFormatter.ofPattern(patron));
    }

    public static String restarDias(long dias, String patron) {
        return LocalDate.now().minusDays(dias).format(DateTimeFormatter.ofPattern(patron));
    }
}
