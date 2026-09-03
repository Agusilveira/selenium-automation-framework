package com.silveira.projects.theinternet.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseTest;
import com.silveira.enums.FailureHandling;
import com.silveira.projects.theinternet.pages.WidgetsPage;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Segundo proyecto: ejercita las partes del framework que SauceDemo no toca.
 *
 * Hasta que existió este archivo, FrameUtils, TableUtils y WindowUtils compilaban
 * pero nunca se habían ejecutado ni una vez.
 */
public class WidgetsTest extends BaseTest {

    private final WidgetsPage widgets = new WidgetsPage();

    @Test(groups = "regresion", description = "Un elemento que carga tarde se detecta sin esperas fijas")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"esperas", "the-internet"})
    public void detectaUnElementoQueCargaConRetraso() {
        assertThat(widgets.textoQueCargaConRetraso())
                .as("texto que aparece despues de la carga diferida")
                .isEqualTo("Hello World!");
    }

    @DataProvider(name = "frames")
    public Object[][] frames() {
        return new Object[][]{
                {new String[]{"frame-top", "frame-left"}, "LEFT"},
                {new String[]{"frame-top", "frame-middle"}, "MIDDLE"},
                {new String[]{"frame-top", "frame-right"}, "RIGHT"},
                {new String[]{"frame-bottom"}, "BOTTOM"}
        };
    }

    @Test(groups = "regresion", dataProvider = "frames",
          description = "Lee contenido dentro de frames anidados y vuelve al documento raiz")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"frames", "the-internet"})
    public void leeDentroDeFramesAnidados(String[] ruta, String esperado) {
        assertThat(widgets.textoDelFrame(ruta))
                .as("texto dentro de %s", String.join(" > ", ruta))
                .isEqualTo(esperado);
    }

    @DataProvider(name = "alertas")
    public Object[][] alertas() {
        return new Object[][]{
                {"alert", "acepto", "You successfully clicked an alert"},
                {"confirm", "acepto", "You clicked: Ok"},
                {"confirm", "descarto", "You clicked: Cancel"}
        };
    }

    @Test(groups = "regresion", dataProvider = "alertas",
          description = "Acepta y descarta alertas de JavaScript")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"alertas", "the-internet"})
    public void manejaAlertas(String tipo, String accion, String esperado) {
        assertThat(widgets.resultadoDeAlerta(tipo, accion))
                .as("resultado tras %s la alerta %s", accion, tipo)
                .isEqualTo(esperado);
    }

    @Test(groups = "regresion", description = "Responde un prompt y el texto llega a la pagina")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"alertas", "the-internet"})
    public void respondeUnPrompt() {
        assertThat(widgets.textoDelPrompt("hola framework"))
                .as("resultado tras responder el prompt")
                .contains("hola framework");
    }

    @Test(groups = "regresion", description = "Cambia a una ventana nueva, la lee y la cierra")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"ventanas", "the-internet"})
    public void cambiaDeVentanaYVuelve() {
        assertThat(widgets.tituloDeLaVentanaNueva())
                .as("titulo de la ventana nueva")
                .isEqualTo("New Window");

        assertThat(widgets.ventanasAbiertas())
                .as("al terminar deberia quedar una sola ventana")
                .isEqualTo(1);
    }

    @Test(groups = "regresion", description = "Lee una tabla por nombre de columna, no por indice")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"tablas", "the-internet"})
    public void leeUnaTablaPorNombreDeColumna() {
        assertThat(widgets.encabezadosDeLaTabla())
                .as("encabezados de la tabla")
                .contains("Last Name", "Email", "Due");

        assertThat(widgets.filasDeLaTabla()).as("filas de la tabla").isEqualTo(4);

        assertThat(widgets.columnaDeLaTabla("Email"))
                .as("columna Email")
                .contains("jdoe@hotmail.com");
    }

    @Test(groups = "regresion", description = "Encuentra una fila por su contenido y la lee entera")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"tablas", "the-internet"})
    public void encuentraUnaFilaPorSuContenido() {
        assertThat(widgets.filaConEmail("jdoe@hotmail.com"))
                .as("fila del email buscado")
                .containsEntry("Last Name", "Doe")
                .containsEntry("First Name", "Jason");
    }

    @Test(groups = "regresion",
          description = "Una accion OPTIONAL que falla no rompe el caso ni lo marca en rojo")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"failure-handling", "the-internet"})
    public void unaAccionOpcionalQueFallaNoRompeElCaso() {
        widgets.abrirCheckboxes();

        // El banner no existe. Con OPTIONAL devuelve false y el caso sigue: es el
        // patron de "cerra el aviso de cookies si aparecio".
        assertThat(widgets.intentarCerrarBannerQueNoExiste())
                .as("la accion opcional deberia informar que no se pudo hacer")
                .isFalse();

        // Si el caso llega hasta aca en verde, OPTIONAL hizo lo suyo.
        assertThat(widgets.ventanasAbiertas()).isEqualTo(1);
    }
}
