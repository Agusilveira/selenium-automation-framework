package com.silveira.common;

import com.silveira.config.ConfigManager;
import com.silveira.driver.DriverManager;
import com.silveira.driver.TargetFactory;
import com.silveira.enums.Browser;
import com.silveira.enums.Target;
import com.silveira.keywords.AlertUtils;
import com.silveira.keywords.WebUI;
import com.silveira.utils.LogUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

/**
 * Base de los tests: se encarga del ciclo de vida del navegador.
 *
 * Un driver por método y no por clase: un test que deja el navegador en un estado
 * raro no debe poder arruinar al siguiente. Cuesta unos segundos por caso y
 * elimina una categoría entera de fallos en cascada difíciles de leer.
 *
 * El navegador y el target se pueden fijar desde el XML de la suite, lo que
 * permite una suite por navegador sin tocar código.
 */
public abstract class BaseTest {

    @Parameters({"browser", "target"})
    @BeforeMethod(alwaysRun = true)
    public void abrirNavegador(@Optional String browser, @Optional String target) {
        ConfigManager config = ConfigManager.get();

        Browser navegador = (browser == null || browser.isBlank())
                ? config.browser()
                : Browser.valueOf(browser.trim().toUpperCase());

        Target destino = (target == null || target.isBlank())
                ? config.target()
                : Target.valueOf(target.trim().toUpperCase());

        LogUtils.info("Abriendo " + navegador + " en " + destino);
        DriverManager.set(TargetFactory.crear(destino, navegador, config.headless()));
        WebUI.abrirUrl(config.baseUrl());
    }

    @AfterMethod(alwaysRun = true)
    public void cerrarNavegador() {
        if (!DriverManager.hayDriver()) return;
        // Una alerta abierta impide cerrar la sesión y deja el proceso colgado.
        AlertUtils.aceptarSiHay();
        DriverManager.quit();
        LogUtils.info("Navegador cerrado");
    }
}
