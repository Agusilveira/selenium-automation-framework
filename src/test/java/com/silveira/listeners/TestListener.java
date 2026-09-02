package com.silveira.listeners;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.driver.DriverManager;
import com.silveira.helpers.CaptureHelper;
import com.silveira.reports.AllureManager;
import com.silveira.reports.ExtentReportManager;
import com.silveira.reports.ExtentTestManager;
import com.silveira.utils.LogUtils;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Arrays;

/**
 * Conecta la ejecución con los reportes.
 *
 * Los tests no llaman al reporte: lo hace el listener. Así un caso se lee como lo
 * que prueba y no como lo que registra, y cambiar de herramienta de reporte no
 * toca ni un test.
 */
public class TestListener implements ITestListener {

    @Override
    public void onStart(ITestContext contexto) {
        LogUtils.info("Iniciando suite: " + contexto.getName());
    }

    @Override
    public void onTestStart(ITestResult resultado) {
        ExtentTestManager.crear(nombre(resultado), descripcion(resultado));
        anotarMetadatos(resultado);
        LogUtils.info("▶ " + nombre(resultado));
    }

    @Override
    public void onTestSuccess(ITestResult resultado) {
        ExtentTestManager.ok("El caso pasó");
        ExtentTestManager.remover();
        LogUtils.info("✔ " + nombre(resultado));
    }

    @Override
    public void onTestFailure(ITestResult resultado) {
        Throwable causa = resultado.getThrowable();
        LogUtils.error("✘ " + nombre(resultado) + ": " + (causa != null ? causa.getMessage() : ""));

        // La evidencia se captura antes de que BaseTest cierre el navegador, y
        // nunca puede tapar el error real: por eso va en un bloque que no lanza.
        if (DriverManager.hayDriver()) {
            ExtentTestManager.falloConEvidencia(causa != null ? causa.getMessage() : "Falló");
            AllureManager.adjuntarScreenshot(nombre(resultado));
            CaptureHelper.aArchivoSinFallar(nombre(resultado));
        } else {
            ExtentTestManager.fallo(causa != null ? causa.getMessage() : "Falló sin navegador");
        }
        ExtentTestManager.remover();
    }

    @Override
    public void onTestSkipped(ITestResult resultado) {
        ExtentTestManager.omitido("El caso se omitió");
        ExtentTestManager.remover();
        LogUtils.warn("↷ " + nombre(resultado) + " omitido");
    }

    @Override
    public void onFinish(ITestContext contexto) {
        ExtentReportManager.volcar();
        LogUtils.info("Suite terminada: " + contexto.getName());
    }

    private String nombre(ITestResult resultado) {
        return resultado.getMethod().getMethodName();
    }

    private String descripcion(ITestResult resultado) {
        FrameworkAnnotation anotacion = anotacion(resultado);
        if (anotacion != null && !anotacion.descripcion().isBlank()) {
            return anotacion.descripcion();
        }
        String descripcion = resultado.getMethod().getDescription();
        return descripcion != null ? descripcion : "";
    }

    private void anotarMetadatos(ITestResult resultado) {
        FrameworkAnnotation anotacion = anotacion(resultado);
        if (anotacion == null) return;
        if (anotacion.autor().length > 0) {
            ExtentTestManager.get().assignAuthor(anotacion.autor());
        }
        if (anotacion.categoria().length > 0) {
            ExtentTestManager.get().assignCategory(anotacion.categoria());
        }
        LogUtils.debug("Metadatos: autor=" + Arrays.toString(anotacion.autor())
                + " categoría=" + Arrays.toString(anotacion.categoria()));
    }

    private FrameworkAnnotation anotacion(ITestResult resultado) {
        return resultado.getMethod()
                .getConstructorOrMethod()
                .getMethod()
                .getAnnotation(FrameworkAnnotation.class);
    }
}
