package com.silveira.listeners;

import com.silveira.config.ConfigManager;
import com.silveira.utils.LogUtils;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Reintenta un caso fallido hasta la cantidad configurada en retry.count.
 *
 * Por defecto es 0: reintentar enmascara intermitencias en vez de exponerlas, y
 * un test que solo pasa a veces es un test que no informa nada. Se sube a 1 en CI
 * unicamente para distinguir una falla real de un hipo de red contra un sitio de
 * terceros, y cada reintento queda logueado para que la intermitencia se vea.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private int intentos = 0;

    @Override
    public boolean retry(ITestResult resultado) {
        int maximo = ConfigManager.get().reintentos();
        if (intentos < maximo) {
            intentos++;
            LogUtils.warn("Reintentando " + resultado.getMethod().getMethodName()
                    + " (intento " + (intentos + 1) + " de " + (maximo + 1) + ")");
            return true;
        }
        return false;
    }
}
