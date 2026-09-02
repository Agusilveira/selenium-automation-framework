package com.silveira.keywords;

import com.silveira.config.ConfigManager;
import com.silveira.driver.DriverManager;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Esperas explícitas. Es lo único que Selenium no da servido y por eso existe
 * esta clase; el resto de WebUI se apoya acá.
 *
 * Todo el framework corre con implicit wait en cero: mezclar los dos mecanismos
 * produce tiempos impredecibles y difíciles de diagnosticar.
 */
public final class WaitUtils {

    private WaitUtils() {
    }

    private static WebDriverWait espera(int segundos) {
        return new WebDriverWait(DriverManager.get(), Duration.ofSeconds(segundos));
    }

    private static int porDefecto() {
        return ConfigManager.get().explicitTimeout();
    }

    public static WebElement visible(By locator) {
        return visible(locator, porDefecto());
    }

    public static WebElement visible(By locator, int segundos) {
        return espera(segundos).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public static WebElement clickeable(By locator) {
        return clickeable(locator, porDefecto());
    }

    public static WebElement clickeable(By locator, int segundos) {
        return espera(segundos).until(ExpectedConditions.elementToBeClickable(locator));
    }

    public static WebElement presente(By locator) {
        return espera(porDefecto()).until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public static List<WebElement> todosVisibles(By locator) {
        return espera(porDefecto())
                .until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    public static void invisible(By locator) {
        espera(porDefecto()).until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public static void urlContiene(String fragmento) {
        espera(porDefecto()).until(ExpectedConditions.urlContains(fragmento));
    }

    public static void textoEs(By locator, String texto) {
        espera(porDefecto()).until(ExpectedConditions.textToBe(locator, texto));
    }

    public static Alert alerta() {
        return espera(porDefecto()).until(ExpectedConditions.alertIsPresent());
    }

    public static <T> T hasta(ExpectedCondition<T> condicion, int segundos) {
        return espera(segundos).until(condicion);
    }

    /** True si la condición se cumple dentro del timeout; false si no. No lanza. */
    public static boolean seCumple(ExpectedCondition<?> condicion, int segundos) {
        try {
            espera(segundos).until(condicion);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public static boolean estaVisible(By locator, int segundos) {
        return seCumple(ExpectedConditions.visibilityOfElementLocated(locator), segundos);
    }
}
