package com.silveira.keywords;

import com.silveira.driver.DriverManager;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.By;

/**
 * Cambio de contexto entre frames.
 *
 * Un switchTo() olvidado es de las causas más difíciles de diagnosticar: el test
 * que falla no es el que cambió de contexto, es el siguiente, buscando elementos
 * en un documento que no es el que cree. Por eso los métodos de lectura de acá
 * siempre vuelven al documento raíz.
 */
public final class FrameUtils {

    private FrameUtils() {
    }

    public static void entrar(int indice) {
        LogUtils.info("Entrando al frame por índice " + indice);
        DriverManager.get().switchTo().frame(indice);
    }

    public static void entrar(String nombreOId) {
        LogUtils.info("Entrando al frame '" + nombreOId + "'");
        DriverManager.get().switchTo().frame(nombreOId);
    }

    public static void entrar(By locator) {
        LogUtils.info("Entrando al frame " + locator);
        DriverManager.get().switchTo().frame(WaitUtils.presente(locator));
    }

    /** Entra por una cadena de frames anidados, buscándolos por nombre. */
    public static void entrarAnidados(String... nombres) {
        volverAlRaiz();
        for (String nombre : nombres) {
            DriverManager.get().switchTo().frame(nombre);
        }
        LogUtils.info("Entrando a frames anidados: " + String.join(" > ", nombres));
    }

    public static void volverAlRaiz() {
        DriverManager.get().switchTo().defaultContent();
    }

    public static void subirUnNivel() {
        DriverManager.get().switchTo().parentFrame();
    }

    /**
     * Lee el texto de un frame anidado y deja el contexto en el documento raíz,
     * sin importar cómo haya salido la lectura.
     */
    public static String textoDentroDe(String... nombres) {
        try {
            entrarAnidados(nombres);
            return WaitUtils.visible(By.tagName("body")).getText().trim();
        } finally {
            volverAlRaiz();
        }
    }
}
