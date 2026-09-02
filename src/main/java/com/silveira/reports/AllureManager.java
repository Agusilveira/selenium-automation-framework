package com.silveira.reports;

import com.silveira.helpers.CaptureHelper;
import com.silveira.utils.LogUtils;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

/**
 * Adjuntos y metadatos para Allure.
 *
 * Extent y Allure conviven a proposito: Extent produce un HTML autocontenido que
 * se puede mandar por mail o publicar tal cual, y Allure da historial entre
 * corridas y agrupacion por severidad. Sirven a lectores distintos.
 */
public final class AllureManager {

    private AllureManager() {
    }

    public static void adjuntarScreenshot(String nombre) {
        try {
            Allure.addAttachment(nombre, new ByteArrayInputStream(CaptureHelper.comoBytes()));
        } catch (Exception e) {
            LogUtils.warn("No se pudo adjuntar el screenshot a Allure: " + e.getMessage());
        }
    }

    public static void adjuntarTexto(String nombre, String contenido) {
        Allure.addAttachment(nombre, "text/plain", contenido, ".txt");
    }

    public static void adjuntarHtml(String nombre, String html) {
        Allure.addAttachment(nombre, "text/html", html, ".html");
    }

    public static void paso(String descripcion) {
        Allure.step(descripcion);
    }

    public static void descripcion(String texto) {
        Allure.description(texto);
    }
}
