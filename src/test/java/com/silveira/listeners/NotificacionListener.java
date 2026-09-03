package com.silveira.listeners;

import com.silveira.notifications.EmailNotifier;
import com.silveira.notifications.ResumenDeCorrida;
import com.silveira.utils.LogUtils;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Avisa una vez por suite, cuando ya no queda nada por correr.
 *
 * Va en ISuiteListener y no en el onFinish de TestListener porque ese corre una
 * vez por cada bloque `test` del XML: la regresión tiene tres, y serían tres
 * mails de la misma corrida.
 *
 * El listener solo arma el resumen y lo entrega. Si mañana hay que sumar
 * Telegram, es una línea más acá y una clase nueva; nada de esto cambia.
 */
public class NotificacionListener implements ISuiteListener {

    @Override
    public void onFinish(ISuite suite) {
        ResumenDeCorrida resumen = ResumenDeCorrida.de(suite);
        // Al log siempre, aunque no haya mail configurado: el resumen es util por
        // si solo, y asi el conteo que viaja en la notificación es el mismo que se
        // puede contrastar contra la salida de la corrida.
        LogUtils.info(resumen.asunto() + " (" + resumen.duracionLegible() + ")");
        EmailNotifier.notificar(resumen);
    }
}
