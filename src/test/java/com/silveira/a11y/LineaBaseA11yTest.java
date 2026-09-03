package com.silveira.a11y;

import com.silveira.annotations.FrameworkAnnotation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La línea base es un guardián, y un guardián que nunca falló no está probado.
 *
 * Estos casos la sabotean a propósito —regla nueva, regla que crece, regla que
 * mejora— y verifican que distinga las tres. Sin esto no habría forma de saber si
 * "no falló nada" significa que la accesibilidad está bien o que la comparación
 * no se está haciendo.
 */
public class LineaBaseA11yTest {

    private static final String PAGINA = "pagina-de-prueba-linea-base";
    private Path archivo;

    private ViolacionA11y violacion(String regla, int elementos) {
        return new ViolacionA11y(regla, "serious", "arreglar " + regla,
                "https://dequeuniversity.com/rules/axe/" + regla,
                java.util.Collections.nCopies(elementos, "[\"#x\"]"));
    }

    @BeforeClass
    public void escribirLineaBase() {
        LineaBaseA11y.guardar(PAGINA, List.of(
                violacion("color-contrast", 3),
                violacion("link-name", 1)));
        archivo = Path.of(com.silveira.config.FrameworkConstants.RUTA_A11Y, PAGINA + ".properties");
        assertThat(archivo).exists();
    }

    @AfterClass(alwaysRun = true)
    public void borrarLineaBase() throws IOException {
        Files.deleteIfExists(archivo);
    }

    // ------------------------------------------------------------------

    @Test(groups = "unit", description = "Lo ya conocido no rompe el build")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"a11y"})
    public void lasViolacionesConocidasNoFallan() {
        List<String> regresiones = LineaBaseA11y.regresiones(PAGINA, List.of(
                violacion("color-contrast", 3),
                violacion("link-name", 1)));

        assertThat(regresiones)
                .as("si lo que ya estaba roto hiciera fallar, la suite queda roja el primer día")
                .isEmpty();
    }

    @Test(groups = "unit", description = "Una regla que no estaba rompe el build")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"a11y"})
    public void unaReglaNuevaEsUnaRegresion() {
        List<String> regresiones = LineaBaseA11y.regresiones(PAGINA, List.of(
                violacion("color-contrast", 3),
                violacion("link-name", 1),
                violacion("image-alt", 2)));

        assertThat(regresiones).hasSize(1);
        assertThat(regresiones.get(0))
                .contains("image-alt")
                .contains("regla nueva")
                .as("el mensaje tiene que llevar a la documentación de la regla")
                .contains("dequeuniversity.com");
    }

    @Test(groups = "unit", description = "Una regla conocida que afecta a mas elementos rompe el build")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"a11y"})
    public void masElementosEnUnaReglaConocidaEsUnaRegresion() {
        List<String> regresiones = LineaBaseA11y.regresiones(PAGINA, List.of(
                violacion("color-contrast", 5),
                violacion("link-name", 1)));

        assertThat(regresiones).hasSize(1);
        assertThat(regresiones.get(0)).contains("color-contrast", "de 3", "a 5");
    }

    @Test(groups = "unit", description = "Arreglar accesibilidad no puede hacer fallar nada")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"a11y"})
    public void mejorarNoEsUnaRegresion() {
        List<String> regresiones = LineaBaseA11y.regresiones(PAGINA, List.of(
                violacion("color-contrast", 1)));

        assertThat(regresiones).isEmpty();
    }

    @Test(groups = "unit", description = "La linea base se guarda ordenada y con las cantidades")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"a11y"})
    public void laLineaBaseGuardaReglaYCantidad() {
        assertThat(LineaBaseA11y.leer(PAGINA))
                .containsEntry("color-contrast", 3)
                .containsEntry("link-name", 1);
    }
}
