package com.silveira.cucumber.hooks;

import com.silveira.config.ConfigManager;
import com.silveira.driver.DriverManager;
import com.silveira.driver.TargetFactory;
import com.silveira.helpers.CaptureHelper;
import com.silveira.keywords.AlertUtils;
import com.silveira.keywords.WebUI;
import com.silveira.utils.LogUtils;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/**
 * Ciclo de vida del navegador para los escenarios de Cucumber.
 *
 * Es el equivalente de BaseTest en el camino TestNG: mismo DriverManager, misma
 * configuracion, misma captura de evidencia. Lo unico que cambia es quien dispara
 * el ciclo.
 */
public class CucumberHooks {

    @Before
    public void abrirNavegador() {
        ConfigManager config = ConfigManager.get();
        DriverManager.set(TargetFactory.crear());
        WebUI.abrirUrl(config.baseUrl());
    }

    /** order mas alto corre primero: la evidencia se saca con el navegador abierto. */
    @After(order = 1)
    public void capturarEvidenciaSiFalla(Scenario escenario) {
        if (!escenario.isFailed() || !DriverManager.hayDriver()) return;
        try {
            escenario.attach(CaptureHelper.comoBytes(), "image/png", escenario.getName());
            CaptureHelper.aArchivoSinFallar(escenario.getName());
        } catch (Exception e) {
            LogUtils.warn("No se pudo capturar evidencia: " + e.getMessage());
        }
    }

    @After(order = 0)
    public void cerrarNavegador() {
        if (!DriverManager.hayDriver()) return;
        AlertUtils.aceptarSiHay();
        DriverManager.quit();
    }
}
