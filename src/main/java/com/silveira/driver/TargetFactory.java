package com.silveira.driver;

import com.silveira.config.ConfigManager;
import com.silveira.enums.Browser;
import com.silveira.enums.Target;
import com.silveira.exceptions.TargetNotValidException;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Crea el WebDriver donde corresponda: local o contra un Selenium Grid.
 *
 * No hay binarios de driver en el repo: Selenium Manager los resuelve en runtime.
 * Un chromedriver.exe versionado deja de servir apenas el navegador se actualiza
 * y convierte al repo en algo que hay que arreglar antes de poder correrlo.
 */
public final class TargetFactory {

    private TargetFactory() {
    }

    /** Usa lo configurado en el perfil activo. */
    public static WebDriver crear() {
        ConfigManager config = ConfigManager.get();
        return crear(config.target(), config.browser(), config.headless());
    }

    public static WebDriver crear(Target target, Browser browser, boolean headless) {
        MutableCapabilities opciones = BrowserFactory.opciones(browser, headless);

        WebDriver driver = switch (target) {
            case LOCAL -> local(browser, opciones);
            case GRID  -> remoto(opciones);
            default -> throw new TargetNotValidException("Target no soportado: " + target);
        };

        ConfigManager config = ConfigManager.get();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.pageLoadTimeout()));
        // Implicit wait en cero: mezclarlo con esperas explícitas produce tiempos
        // impredecibles y difíciles de diagnosticar. Toda la espera vive en WaitUtils.
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        return driver;
    }

    private static WebDriver local(Browser browser, MutableCapabilities opciones) {
        return switch (browser) {
            case CHROME  -> new ChromeDriver((ChromeOptions) opciones);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) opciones);
            case EDGE    -> new EdgeDriver((EdgeOptions) opciones);
        };
    }

    private static WebDriver remoto(MutableCapabilities opciones) {
        String url = ConfigManager.get().gridUrl();
        try {
            return new RemoteWebDriver(URI.create(url).toURL(), opciones);

        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new TargetNotValidException("La URL del Grid no es válida: '" + url + "'.", e);

        } catch (RuntimeException e) {
            // El error crudo de Selenium cuando el Grid no responde es una pared de
            // texto que no dice qué hacer. Este dice exactamente qué falta.
            throw new TargetNotValidException("""
                    No se pudo crear una sesion en el Selenium Grid de %s.
                    Levantalo con: docker compose -f docker-compose.grid.yml up -d
                    Y verifica que este listo en %s/status
                    Motivo: %s""".formatted(url, url, e.getMessage()), e);
        }
    }
}
