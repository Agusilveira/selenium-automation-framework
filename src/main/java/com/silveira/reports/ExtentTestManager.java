package com.silveira.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.silveira.helpers.CaptureHelper;

/**
 * La entrada del reporte correspondiente al test del hilo actual.
 *
 * Con suites paralelas, varios tests escriben al reporte al mismo tiempo. Sin
 * ThreadLocal, los pasos de un caso terminan apareciendo dentro de otro.
 */
public final class ExtentTestManager {

    private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

    private ExtentTestManager() {
    }

    public static void crear(String nombre, String descripcion) {
        TEST.set(ExtentReportManager.get().createTest(nombre, descripcion));
    }

    public static ExtentTest get() {
        return TEST.get();
    }

    public static void remover() {
        TEST.remove();
    }

    private static boolean hay() {
        return TEST.get() != null;
    }

    public static void info(String mensaje) {
        if (hay()) TEST.get().log(Status.INFO, mensaje);
    }

    public static void ok(String mensaje) {
        if (hay()) TEST.get().log(Status.PASS, mensaje);
    }

    public static void advertencia(String mensaje) {
        if (hay()) TEST.get().log(Status.WARNING, mensaje);
    }

    public static void fallo(String mensaje) {
        if (hay()) TEST.get().log(Status.FAIL, mensaje);
    }

    public static void omitido(String mensaje) {
        if (hay()) TEST.get().log(Status.SKIP, mensaje);
    }

    /** Adjunta un screenshot embebido, así el HTML viaja solo sin carpeta de imágenes. */
    public static void falloConEvidencia(String mensaje) {
        if (!hay()) return;
        try {
            TEST.get().log(Status.FAIL, mensaje,
                    MediaEntityBuilder.createScreenCaptureFromBase64String(
                            CaptureHelper.comoBase64()).build());
        } catch (Exception e) {
            TEST.get().log(Status.FAIL, mensaje + " (no se pudo capturar evidencia)");
        }
    }
}
