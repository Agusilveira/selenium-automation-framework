package com.silveira.listeners;

import com.silveira.keywords.SoftFailures;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Da vuelta el resultado a FAILURE cuando el caso acumuló fallos blandos.
 *
 * Es la pieza que hace honesto a CONTINUE_ON_FAILURE. Sin esto, un caso que tuvo
 * fallos tolerados termina en verde y la opción se convierte en una forma elegante
 * de esconder errores.
 *
 * Va en afterInvocation y no en un @AfterMethod por dos razones. La primera es que
 * corre antes de ITestListener.onTestSuccess, así que cuando TestListener arma el
 * reporte el caso ya figura como fallado. La segunda es que un @AfterMethod que
 * lanza marca un fallo de configuración, no del test: el reporte diría que el caso
 * pasó y que después algo raro ocurrió al limpiar.
 *
 * También descarta la alternativa de que cada test llame a un verificar() al final:
 * uno que se olvide es un verde silencioso, que es exactamente lo que esto viene a
 * evitar.
 */
public class SoftFailureListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod metodo, ITestResult resultado) {
        if (metodo.isTestMethod()) {
            // El hilo viene de un pool y puede traer fallos del caso anterior.
            SoftFailures.limpiar();
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod metodo, ITestResult resultado) {
        if (!metodo.isTestMethod()) return;

        try {
            if (!SoftFailures.hay()) return;

            String resumen = SoftFailures.resumen();

            // Si el caso ya venía fallando, el fallo duro es la causa principal:
            // los blandos se suman como contexto en vez de reemplazarlo.
            if (resultado.getStatus() == ITestResult.FAILURE && resultado.getThrowable() != null) {
                resultado.setThrowable(new AssertionError(
                        resultado.getThrowable().getMessage() + "\n\nAdemás: " + resumen,
                        resultado.getThrowable()));
            } else {
                resultado.setStatus(ITestResult.FAILURE);
                resultado.setThrowable(new AssertionError(resumen));
            }
        } finally {
            SoftFailures.limpiar();
        }
    }
}
