package com.silveira.keywords;

import com.silveira.config.ConfigManager;
import com.silveira.driver.DriverManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.JavascriptExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Manejo de ventanas y pestañas. */
public final class WindowUtils {

    private WindowUtils() {
    }

    /**
     * Espera a que la ventana recien enfocada tenga contenido real.
     *
     * Cambiar de ventana es instantaneo, cargarla no. Sin esta espera, leer el
     * titulo justo despues del switch devuelve vacio.
     *
     * No alcanza con document.readyState: Firefox abre la pestaña en about:blank y
     * navega despues, y about:blank ya reporta "complete". Por eso la condicion
     * incluye haber salido de about:blank. Chrome no expone el problema porque
     * navega antes de que el handle este disponible, asi que esto solo aparece en
     * el navegador que uno no estaba mirando.
     */
    private static void esperarQueCargue() {
        WaitUtils.hasta(driver -> {
            String url = driver.getCurrentUrl();
            if (url == null || url.isBlank() || url.startsWith("about:")) return false;
            return "complete".equals(
                    ((JavascriptExecutor) driver).executeScript("return document.readyState"));
        }, ConfigManager.get().explicitTimeout());
    }

    public static Set<String> handles() {
        return DriverManager.get().getWindowHandles();
    }

    public static String handleActual() {
        return DriverManager.get().getWindowHandle();
    }

    public static int cantidad() {
        return handles().size();
    }

    public static void cambiarA(int indice) {
        List<String> ventanas = new ArrayList<>(handles());
        if (indice < 0 || indice >= ventanas.size()) {
            throw new FrameworkException(
                    "No existe la ventana con índice " + indice
                    + ". Hay " + ventanas.size() + " ventana(s) abierta(s).");
        }
        DriverManager.get().switchTo().window(ventanas.get(indice));
        esperarQueCargue();
        LogUtils.info("Cambiando a la ventana " + indice);
    }

    public static void cambiarAlTitulo(String titulo) {
        String original = handleActual();
        for (String handle : handles()) {
            DriverManager.get().switchTo().window(handle);
            esperarQueCargue();
            if (DriverManager.get().getTitle().contains(titulo)) {
                LogUtils.info("Cambiando a la ventana con título '" + titulo + "'");
                return;
            }
        }
        DriverManager.get().switchTo().window(original);
        throw new FrameworkException("No hay ninguna ventana con el título '" + titulo + "'");
    }

    public static void volverALaPrincipal() {
        cambiarA(0);
    }

    /**
     * Cierra todas las ventanas menos la principal y vuelve a ella.
     *
     * La lista de handles se toma antes de empezar a cerrar: iterar sobre la
     * colección viva mientras se la modifica da resultados impredecibles.
     */
    public static void cerrarLasDemas() {
        List<String> ventanas = new ArrayList<>(handles());
        String principal = ventanas.get(0);

        for (int i = 1; i < ventanas.size(); i++) {
            DriverManager.get().switchTo().window(ventanas.get(i));
            DriverManager.get().close();
        }

        DriverManager.get().switchTo().window(principal);
        LogUtils.info("Cerradas " + (ventanas.size() - 1) + " ventana(s) secundaria(s)");
    }
}
