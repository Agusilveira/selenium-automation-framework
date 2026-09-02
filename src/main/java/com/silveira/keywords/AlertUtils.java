package com.silveira.keywords;

import com.silveira.driver.DriverManager;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;

/** Alertas nativas de JavaScript: alert, confirm y prompt. */
public final class AlertUtils {

    private AlertUtils() {
    }

    public static void aceptar() {
        Alert alerta = WaitUtils.alerta();
        LogUtils.info("Aceptando alerta: " + alerta.getText());
        alerta.accept();
    }

    public static void descartar() {
        Alert alerta = WaitUtils.alerta();
        LogUtils.info("Descartando alerta: " + alerta.getText());
        alerta.dismiss();
    }

    public static String obtenerTexto() {
        return WaitUtils.alerta().getText();
    }

    /** Escribe en un prompt y acepta. */
    public static void responder(String texto) {
        Alert alerta = WaitUtils.alerta();
        LogUtils.info("Respondiendo al prompt con '" + texto + "'");
        alerta.sendKeys(texto);
        alerta.accept();
    }

    /** Sin espera: responde por el estado actual, no por el futuro. */
    public static boolean hayAlerta() {
        try {
            DriverManager.get().switchTo().alert();
            return true;
        } catch (NoAlertPresentException e) {
            return false;
        }
    }

    /**
     * Acepta si hay una alerta, y si no sigue de largo.
     * Útil en teardown, donde una alerta colgada impide cerrar el navegador.
     */
    public static void aceptarSiHay() {
        if (hayAlerta()) {
            DriverManager.get().switchTo().alert().accept();
            LogUtils.info("Se aceptó una alerta pendiente");
        }
    }
}
