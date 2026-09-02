package com.silveira.helpers;

import com.silveira.exceptions.InvalidPathException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Operaciones de archivos.
 *
 * Todas las rutas se resuelven contra el directorio de trabajo del proyecto, no
 * contra el classpath: un recurso empaquetado no se puede escribir, y estos
 * helpers se usan tanto para leer datos como para dejar evidencia.
 */
public final class FileHelper {

    private FileHelper() {
    }

    public static Path ruta(String relativa) {
        return Path.of(System.getProperty("user.dir")).resolve(relativa).normalize();
    }

    /** Verifica que exista y sea legible; si no, falla nombrando la ruta. */
    public static Path exigir(String relativa) {
        Path path = ruta(relativa);
        if (!Files.isReadable(path)) {
            throw new InvalidPathException("No existe o no se puede leer: " + path);
        }
        return path;
    }

    public static boolean existe(String relativa) {
        return Files.exists(ruta(relativa));
    }

    public static Path crearDirectorios(String relativa) {
        Path path = ruta(relativa);
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo crear el directorio " + path, e);
        }
    }

    public static String leerTexto(String relativa) {
        try {
            return Files.readString(exigir(relativa));
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo leer " + relativa, e);
        }
    }

    public static void escribirTexto(String relativa, String contenido) {
        Path path = ruta(relativa);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, contenido);
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo escribir " + path, e);
        }
    }

    public static List<Path> listar(String directorio, String extension) {
        Path path = exigir(directorio);
        try (Stream<Path> archivos = Files.list(path)) {
            return archivos
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo listar " + path, e);
        }
    }

    /** Borra un archivo o un directorio con todo su contenido. */
    public static void borrar(String relativa) {
        Path path = ruta(relativa);
        if (!Files.exists(path)) return;
        try (Stream<Path> contenido = Files.walk(path)) {
            contenido.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new InvalidPathException("No se pudo borrar " + p, e);
                }
            });
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo recorrer " + path, e);
        }
    }
}
