package com.silveira.notifications;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.notifications.ResumenDeCorrida.CasoFallado;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El motor de plantillas, con los cuatro casos que lo pueden romper.
 *
 * El que más importa es el escapado: el texto que entra son mensajes de error de
 * tests que fallaron, y ahí aparece cualquier cosa. Un `<` sin escapar rompe el
 * HTML del mail justo en la corrida en la que hacía falta leerlo.
 */
public class PlantillaTest {

    @Test(groups = "unit", description = "Reemplaza variables y escapa el HTML")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void reemplazaYEscapa() {
        String salida = Plantilla.renderHtml("Hola {{quien}}",
                Map.of("quien", "<b>mundo</b>"));

        assertThat(salida)
                .as("el valor tiene que llegar escapado, no interpretado")
                .isEqualTo("Hola &lt;b&gt;mundo&lt;/b&gt;");
    }

    @Test(groups = "unit", description = "En texto plano no escapa")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void enTextoPlanoNoEscapa() {
        assertThat(Plantilla.renderTexto("[{{estado}}] {{suite}}",
                Map.of("estado", "FALLÓ", "suite", "Regresión & co")))
                .isEqualTo("[FALLÓ] Regresión & co");
    }

    @Test(groups = "unit", description = "Un parametro mal escrito queda visible")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void unParametroInexistenteNoDesaparece() {
        assertThat(Plantilla.renderHtml("total: {{totl}}", Map.of("total", 58)))
                .as("si se borrara, un error de tipeo se descubre recién leyendo el mail")
                .isEqualTo("total: {{totl}}");
    }

    @Test(groups = "unit", description = "Una lista repite el bloque una vez por elemento")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void unaListaRepiteElBloque() {
        Map<String, Object> valores = Map.of("fallos", List.of(
                Map.of("clase", "LoginTest", "metodo", "usuarioBloqueado"),
                Map.of("clase", "CheckoutTest", "metodo", "compraCompleta")));

        assertThat(Plantilla.renderHtml("{{#fallos}}[{{clase}}.{{metodo}}]{{/fallos}}", valores))
                .isEqualTo("[LoginTest.usuarioBloqueado][CheckoutTest.compraCompleta]");
    }

    @Test(groups = "unit", description = "Las secciones condicionales incluyen o excluyen el bloque")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void lasSeccionesCondicionalesFuncionanEnLosDosSentidos() {
        String plantilla = "{{#hayFallos}}rojo{{/hayFallos}}{{^hayFallos}}verde{{/hayFallos}}";

        assertThat(Plantilla.renderHtml(plantilla, Map.of("hayFallos", true))).isEqualTo("rojo");
        assertThat(Plantilla.renderHtml(plantilla, Map.of("hayFallos", false))).isEqualTo("verde");
        assertThat(Plantilla.renderHtml(plantilla, Map.of("hayFallos", ""))).isEqualTo("verde");
    }

    @Test(groups = "unit", description = "Una lista dentro de una condicion se renderiza bien")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void unaListaDentroDeUnaCondicion() {
        // Es la forma exacta que tiene la plantilla por defecto: el <ul> va una
        // sola vez y los <li> uno por caso. Sin anidado habria que partir el <ul>
        // en dos secciones, que es lo que se queria evitar.
        String plantilla = "{{#hayFallos}}<ul>{{#fallos}}<li>{{clase}}</li>{{/fallos}}</ul>{{/hayFallos}}";
        Map<String, Object> valores = Map.of(
                "hayFallos", true,
                "fallos", List.of(Map.of("clase", "A"), Map.of("clase", "B")));

        assertThat(Plantilla.renderHtml(plantilla, valores))
                .isEqualTo("<ul><li>A</li><li>B</li></ul>");
    }

    // ------------------------------------------------------------------

    @Test(groups = "unit", description = "El resumen se puede renderizar con una plantilla propia")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void elResumenAceptaUnaPlantillaPropia() {
        ResumenDeCorrida resumen = new ResumenDeCorrida("Regresion", "ci", "chrome", 56, 2, 0,
                Duration.ofSeconds(94),
                List.of(new CasoFallado("CheckoutTest", "compraCompleta", "no apareció el botón")),
                "https://ejemplo/reporte");

        String salida = resumen.comoHtml(
                "{{estado}}|{{suite}}|{{pasados}}/{{total}}|{{#fallos}}{{nombreCompleto}}{{/fallos}}");

        assertThat(salida).isEqualTo("FALLÓ|Regresion|56/58|CheckoutTest.compraCompleta");
    }

    @Test(groups = "unit", description = "La plantilla empaquetada arma un cuerpo valido")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void laPlantillaEmpaquetadaSeRenderiza() {
        ResumenDeCorrida verde = new ResumenDeCorrida("Regresion", "ci", "chrome", 58, 0, 0,
                Duration.ofSeconds(88), List.of(), "");

        String salida = verde.comoHtml();

        assertThat(salida)
                .as("en verde no debería aparecer el bloque de casos fallados")
                .contains("todo verde")
                .doesNotContain("Casos fallados")
                .doesNotContain("Ver el reporte completo");
        assertThat(salida)
                .as("no debería quedar sintaxis de plantilla sin resolver")
                .doesNotContain("{{");
    }
}
