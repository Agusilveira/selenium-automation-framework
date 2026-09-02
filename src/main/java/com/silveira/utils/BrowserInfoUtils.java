package com.silveira.utils;

import com.silveira.driver.DriverManager;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Datos del navegador y del entorno, para el encabezado de los reportes.
 *
 * Sin esto, un reporte que falla no dice contra qué navegador ni en qué sistema
 * corrio, que es lo primero que se pregunta quien lo abre.
 */
public final class BrowserInfoUtils {

    private BrowserInfoUtils() {
    }

    private static Capabilities capacidades() {
        return ((RemoteWebDriver) DriverManager.get()).getCapabilities();
    }

    public static String navegador() {
        return capacidades().getBrowserName();
    }

    public static String version() {
        return capacidades().getBrowserVersion();
    }

    public static String sistemaOperativo() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    public static String versionDeJava() {
        return System.getProperty("java.version");
    }

    /** Una línea con todo, lista para el reporte. */
    public static String resumen() {
        return navegador() + " " + version() + " sobre " + sistemaOperativo()
                + " (Java " + versionDeJava() + ")";
    }
}
