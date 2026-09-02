package com.silveira.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.silveira.exceptions.FrameworkException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Lectura y escritura de JSON, para datos de test y configuración. */
public final class JsonHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonHelper() {
    }

    public static Map<String, Object> comoMapa(String rutaRelativa) {
        return leer(rutaRelativa, new TypeReference<>() {
        });
    }

    /** Un array de objetos como lista de mapas: la forma que consumen los DataProviders. */
    public static List<Map<String, Object>> comoLista(String rutaRelativa) {
        return leer(rutaRelativa, new TypeReference<>() {
        });
    }

    public static <T> T comoObjeto(String rutaRelativa, Class<T> tipo) {
        try {
            return MAPPER.readValue(Files.readString(FileHelper.exigir(rutaRelativa)), tipo);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer " + rutaRelativa + " como " + tipo, e);
        }
    }

    public static <T> List<T> comoListaDe(String rutaRelativa, Class<T> tipo) {
        try {
            return MAPPER.readValue(
                    Files.readString(FileHelper.exigir(rutaRelativa)),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, tipo));
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer " + rutaRelativa + " como lista de " + tipo, e);
        }
    }

    private static <T> T leer(String rutaRelativa, TypeReference<T> tipo) {
        try {
            return MAPPER.readValue(Files.readString(FileHelper.exigir(rutaRelativa)), tipo);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer el JSON " + rutaRelativa, e);
        }
    }

    public static void escribir(String rutaRelativa, Object contenido) {
        Path path = FileHelper.ruta(rutaRelativa);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), contenido);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo escribir el JSON " + path, e);
        }
    }

    public static String aTexto(Object objeto) {
        try {
            return MAPPER.writeValueAsString(objeto);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo serializar a JSON", e);
        }
    }
}
