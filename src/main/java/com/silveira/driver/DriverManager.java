package com.silveira.driver;

import com.silveira.exceptions.FrameworkException;
import org.openqa.selenium.WebDriver;

/**
 * Guarda el WebDriver del hilo actual.
 *
 * Con TestNG y suites paralelas no hay contenedor que aísle por caso: cada hilo
 * corre sus tests y necesita su propio driver. Por eso acá ThreadLocal se gana
 * el lugar, a diferencia de un runner que ya inyecta contexto por escenario.
 *
 * remove() en el teardown no es opcional: sin él, el hilo del pool conserva la
 * referencia a un driver ya cerrado y el test siguiente lo recibe muerto.
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static WebDriver get() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new FrameworkException(
                    "No hay WebDriver para este hilo. ¿El test extiende BaseTest "
                    + "o alguien llamó a DriverManager.set() antes?");
        }
        return driver;
    }

    public static void set(WebDriver driver) {
        DRIVER.set(driver);
    }

    public static boolean hayDriver() {
        return DRIVER.get() != null;
    }

    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            driver.quit();
            DRIVER.remove();
        }
    }
}
