package com.silveira.keywords;

import com.silveira.driver.DriverManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Manejo de ventanas y pestañas. */
public final class WindowUtils {

    private WindowUtils() {
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
        LogUtils.info("Cambiando a la ventana " + indice);
    }

    public static void cambiarAlTitulo(String titulo) {
        String original = handleActual();
        for (String handle : handles()) {
            DriverManager.get().switchTo().window(handle);
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
