package com.silveira.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.silveira.config.ConfigManager;
import com.silveira.config.FrameworkConstants;
import com.silveira.utils.DateUtils;

/**
 * Reporte HTML de la ejecución.
 *
 * Uno solo por corrida, compartido por todos los hilos. Cada test tiene su propia
 * entrada, y de eso se ocupa ExtentTestManager.
 */
public final class ExtentReportManager {

    private static ExtentReports reporte;

    private ExtentReportManager() {
    }

    public static synchronized ExtentReports get() {
        if (reporte == null) iniciar();
        return reporte;
    }

    private static void iniciar() {
        ExtentSparkReporter spark = new ExtentSparkReporter(FrameworkConstants.REPORTE_EXTENT);
        spark.config().setTheme(Theme.DARK);
        spark.config().setDocumentTitle("Reporte de automatización");
        spark.config().setReportName("selenium-automation-framework");
        spark.config().setTimeStampFormat(DateUtils.FORMATO_FECHA_HORA);

        reporte = new ExtentReports();
        reporte.attachReporter(spark);

        // El encabezado responde lo primero que se pregunta quien abre un reporte:
        // contra qué ambiente y con qué navegador corrió.
        ConfigManager config = ConfigManager.get();
        reporte.setSystemInfo("Ambiente", System.getProperty("env", "local"));
        reporte.setSystemInfo("URL base", config.get("base.url", "no definida"));
        reporte.setSystemInfo("Navegador", config.get("browser", "no definido"));
        reporte.setSystemInfo("Headless", String.valueOf(config.get("headless", "?")));
        reporte.setSystemInfo("Sistema", System.getProperty("os.name"));
        reporte.setSystemInfo("Java", System.getProperty("java.version"));
        reporte.setSystemInfo("Ejecutado", DateUtils.ahora());
    }

    /** Vuelca el reporte a disco. Sin esto el archivo queda vacío. */
    public static synchronized void volcar() {
        if (reporte != null) reporte.flush();
    }
}
