package com.silveira.driver;

import com.silveira.enums.Browser;
import com.silveira.exceptions.BrowserNotSupportedException;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Construye las opciones de cada navegador.
 *
 * Devuelve opciones y no drivers a propósito: las mismas opciones sirven para un
 * driver local y para un RemoteWebDriver contra un Grid. Quién instancia es
 * TargetFactory.
 */
public final class BrowserFactory {

    /**
     * Selenium avisa por cada driver cuando no encuentra una versión de CDP que
     * coincida con el navegador instalado. Este framework no usa DevTools, así que
     * el aviso solo ensucia la salida.
     *
     * Son campos y no variables locales a propósito: java.util.logging mantiene
     * los loggers con referencias débiles, y uno sin referencia fuerte se recolecta
     * y vuelve al nivel por defecto.
     */
    private static final Logger[] SILENCIADOS = {
            Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder"),
            Logger.getLogger("org.openqa.selenium.devtools"),
            Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver"),
            Logger.getLogger("org.openqa.selenium.chromium")
    };

    static {
        for (Logger logger : SILENCIADOS) logger.setLevel(Level.SEVERE);
    }

    private BrowserFactory() {
    }

    public static MutableCapabilities opciones(Browser browser, boolean headless) {
        return switch (browser) {
            case CHROME  -> chrome(headless);
            case FIREFOX -> firefox(headless);
            case EDGE    -> edge(headless);
            default -> throw new BrowserNotSupportedException("Navegador no soportado: " + browser);
        };
    }

    /**
     * El tamaño de ventana se fija al arrancar y no con window().setSize() después.
     *
     * En headless la superficie de render nace chica. Un setSize posterior actualiza
     * el viewport que reporta JavaScript, pero no esa superficie: los clicks se
     * siguen despachando contra la vieja y no llegan a la página. El síntoma es un
     * click sobre un elemento visible, habilitado y sin nada encima que simplemente
     * no hace nada, y engaña porque window.innerWidth informa el tamaño nuevo.
     *
     * Tampoco va --disable-gpu: en --headless=new es innecesario y desactiva el
     * compositor, que es justamente quien entrega los eventos de entrada.
     */
    private static ChromeOptions chrome(boolean headless) {
        ChromeOptions opciones = new ChromeOptions();
        if (headless) opciones.addArguments("--headless=new");
        opciones.addArguments("--window-size=1280,1024");
        opciones.addArguments(
                "--disable-notifications",
                "--no-sandbox",
                // Los contenedores de CI montan un /dev/shm chico y Chrome se queda
                // sin memoria compartida.
                "--disable-dev-shm-usage",
                // Cuando Chrome da una ventana por ocluida o en segundo plano
                // descarta los eventos de entrada.
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling");
        return opciones;
    }

    private static FirefoxOptions firefox(boolean headless) {
        FirefoxOptions opciones = new FirefoxOptions();
        if (headless) opciones.addArguments("-headless");
        opciones.addArguments("--width=1280", "--height=1024");
        return opciones;
    }

    private static EdgeOptions edge(boolean headless) {
        EdgeOptions opciones = new EdgeOptions();
        if (headless) opciones.addArguments("--headless=new");
        opciones.addArguments("--window-size=1280,1024", "--no-sandbox", "--disable-dev-shm-usage");
        return opciones;
    }
}
