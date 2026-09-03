package com.silveira.listeners;

import com.silveira.config.ConfigManager;
import com.silveira.keywords.FallbackTracker;
import com.silveira.reports.ExtentReportManager;
import com.silveira.utils.LogUtils;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Resume el uso del recurso a JavaScript y lo publica en el reporte.
 *
 * No hace fallar la suite: de eso se encarga FallbackGuardTest, que corre como un
 * caso más. Una excepción lanzada desde acá rompe el build pero deja a surefire
 * sin poder informar cuántos tests corrieron, así que el build queda en rojo sin
 * decir qué pasó con el resto.
 */
public class FallbackGuardListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        FallbackTracker.limpiar();
    }

    @Override
    public void onFinish(ISuite suite) {
        int usos = FallbackTracker.cantidad();
        int maximo = ConfigManager.get().fallbackJsMaximo();
        String resumen = FallbackTracker.resumen();

        if (usos == 0) {
            LogUtils.info(resumen);
        } else {
            LogUtils.warn(resumen);
        }

        // En el encabezado del reporte, donde se ve sin tener que buscarlo.
        ExtentReportManager.get().setSystemInfo("Recursos a JavaScript",
                usos + " (máximo tolerado: " + maximo + ")");
        ExtentReportManager.volcar();
    }
}
