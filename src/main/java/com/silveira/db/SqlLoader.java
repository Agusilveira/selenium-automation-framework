package com.silveira.db;

import com.silveira.helpers.FileHelper;
import com.silveira.utils.LogUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carga consultas desde archivos .sql.
 *
 * Es el mismo criterio que los locators externalizados: una consulta en un archivo
 * .sql la puede leer, revisar y corregir alguien que sepa SQL pero no Java, y no
 * obliga a recompilar. Ademas se edita con resaltado de sintaxis en vez de vivir
 * concatenada dentro de un String de Java.
 */
public final class SqlLoader {

    private static final String RUTA = "src/test/resources/sql/";
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private SqlLoader() {
    }

    /** Contenido de sql/<nombre>.sql. Se lee del disco una sola vez. */
    public static String cargar(String nombre) {
        return CACHE.computeIfAbsent(nombre, n -> {
            String contenido = FileHelper.leerTexto(RUTA + n + ".sql");
            LogUtils.debug("SQL cargado desde " + n + ".sql (" + contenido.length() + " caracteres)");
            return contenido;
        });
    }

    public static void limpiarCache() {
        CACHE.clear();
    }
}
