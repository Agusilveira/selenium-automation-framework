package com.silveira.helpers;

import com.silveira.config.FrameworkConstants;
import com.silveira.exceptions.ConfigKeyMissingException;
import com.silveira.exceptions.InvalidPathException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Lectura de archivos .properties, con foco en los locators.
 *
 * Tener los locators en archivos y no en constantes Java significa que cambiar un
 * selector no requiere recompilar ni tocar código: es la diferencia entre que un
 * cambio de front lo arregle cualquiera del equipo o solo quien sepa Java.
 */
public final class PropertiesHelper {

    private static final Map<String, Properties> CACHE = new LinkedHashMap<>();

    private PropertiesHelper() {
    }

    public static Properties cargar(String rutaRelativa) {
        return CACHE.computeIfAbsent(rutaRelativa, ruta -> {
            Path path = FileHelper.exigir(ruta);
            Properties propiedades = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                propiedades.load(in);
                return propiedades;
            } catch (IOException e) {
                throw new InvalidPathException("No se pudo leer " + path, e);
            }
        });
    }

    /** Carga todos los .properties de objects/ en un solo mapa de locators. */
    public static Properties cargarLocators() {
        return CACHE.computeIfAbsent("__locators__", clave -> {
            Properties todos = new Properties();
            for (Path archivo : FileHelper.listar(FrameworkConstants.RUTA_OBJECTS, ".properties")) {
                try (InputStream in = Files.newInputStream(archivo)) {
                    todos.load(in);
                } catch (IOException e) {
                    throw new InvalidPathException("No se pudo leer " + archivo, e);
                }
            }
            return todos;
        });
    }

    public static String get(String rutaRelativa, String clave) {
        String valor = cargar(rutaRelativa).getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new ConfigKeyMissingException(
                    "El archivo " + rutaRelativa + " no define la clave '" + clave + "'");
        }
        return valor;
    }

    /** Locator por clave, buscando en todos los archivos de objects/. */
    public static String locator(String clave) {
        String valor = cargarLocators().getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new ConfigKeyMissingException(
                    "No hay ningún locator definido con la clave '" + clave + "' en "
                    + FrameworkConstants.RUTA_OBJECTS);
        }
        return valor;
    }

    public static void set(String rutaRelativa, String clave, String valor) {
        Properties propiedades = cargar(rutaRelativa);
        propiedades.setProperty(clave, valor);
        try (OutputStream out = Files.newOutputStream(FileHelper.ruta(rutaRelativa))) {
            propiedades.store(out, null);
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo escribir " + rutaRelativa, e);
        }
    }

    /** Vacía la caché. Necesario en tests que modifican archivos entre casos. */
    public static void limpiarCache() {
        CACHE.clear();
    }
}
