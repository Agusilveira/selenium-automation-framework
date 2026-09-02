package com.silveira.helpers;

import com.silveira.config.FrameworkConstants;
import com.silveira.driver.DriverManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.DateUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Captura de evidencia. Devuelve la misma imagen en los tres formatos que
 * consumen los reportes: bytes para adjuntar, base64 para embeber en HTML, y
 * archivo en disco para subir como artefacto del CI.
 */
public final class CaptureHelper {

    private CaptureHelper() {
    }

    public static byte[] comoBytes() {
        return ((TakesScreenshot) DriverManager.get()).getScreenshotAs(OutputType.BYTES);
    }

    public static String comoBase64() {
        return Base64.getEncoder().encodeToString(comoBytes());
    }

    /**
     * Guarda el screenshot y devuelve la ruta. El nombre lleva timestamp para que
     * dos fallos del mismo caso no se pisen entre sí.
     */
    public static Path aArchivo(String nombre) {
        String limpio = nombre.replaceAll("[^a-zA-Z0-9._-]+", "-");
        Path destino = Path.of(FrameworkConstants.RUTA_EVIDENCIA,
                limpio + "_" + DateUtils.timestampParaArchivo() + ".png");
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, comoBytes());
            return destino;
        } catch (IOException e) {
            throw new FrameworkException("No se pudo guardar el screenshot en " + destino, e);
        }
    }

    /** Igual que aArchivo pero no lanza: la evidencia nunca debe tapar el error real. */
    public static Path aArchivoSinFallar(String nombre) {
        try {
            return aArchivo(nombre);
        } catch (Exception e) {
            com.silveira.utils.LogUtils.warn("No se pudo capturar evidencia: " + e.getMessage());
            return null;
        }
    }
}
