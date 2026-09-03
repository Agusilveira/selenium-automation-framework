package com.silveira.guards;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.config.ConfigManager;
import com.silveira.keywords.FallbackTracker;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el framework no haya tenido que recurrir a JavaScript más veces de
 * las toleradas.
 *
 * Es un caso de test y no una excepción desde un listener por dos razones. Una
 * práctica: una excepción en ISuiteListener rompe el build pero deja a surefire
 * sin informar cuántos tests corrieron, y el resultado es un rojo que no dice
 * nada. La otra es conceptual: "el framework no necesitó salteárselas más de N
 * veces" es una afirmación sobre la corrida, o sea exactamente un test.
 *
 * Va en un bloque de suite propio al final, para que corra después de todo lo
 * demás y vea el total real.
 */
public class FallbackGuardTest {

    @Test(alwaysRun = true,
          description = "El recurso a JavaScript se mantuvo dentro del umbral")
    @FrameworkAnnotation(autor = "Framework", categoria = "guardias")
    public void elRecursoAJavaScriptNoSePasoDelUmbral() {
        int usos = FallbackTracker.cantidad();
        int maximo = ConfigManager.get().fallbackJsMaximo();

        assertThat(usos)
                .as("El framework recurrió a JavaScript %d vez/veces porque el navegador "
                    + "no entregó el evento nativo. No subas el máximo para que pase: "
                    + "revisá qué elemento nuevo lo está necesitando.%n%s",
                    usos, FallbackTracker.resumen())
                .isLessThanOrEqualTo(maximo);
    }
}
