package com.silveira.common;

import com.silveira.api.AuthManager;
import com.silveira.utils.LogUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Base de los tests de API. Sin navegador.
 *
 * Que exista una base separada de BaseTest, y no un flag en la misma, es la
 * diferencia entre correr 20 casos de API en dos segundos o levantar 20 Chrome
 * para no usarlos.
 */
public abstract class BaseApiTest {

    @BeforeMethod(alwaysRun = true)
    public void prepararContexto() {
        LogUtils.info("Caso de API sin navegador");
    }

    /**
     * El token se descarta entre casos. Uno que se autentica como otro usuario no
     * puede dejarle esa sesion al siguiente: seria un fallo dificil de leer, en un
     * caso que no tiene nada que ver.
     */
    @AfterMethod(alwaysRun = true)
    public void limpiarSesion() {
        AuthManager.limpiar();
    }
}
