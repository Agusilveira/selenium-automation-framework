package com.silveira.a11y;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.silveira.config.ConfigManager;
import com.silveira.driver.DriverManager;
import com.silveira.exceptions.AccesibilidadException;
import com.silveira.reports.AllureManager;
import com.silveira.reports.ExtentTestManager;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Comparator;
import java.util.List;

/**
 * Corre axe-core sobre la pagina abierta y traduce el resultado a algo legible.
 *
 * axe-core es el mismo motor que usan las extensiones de navegador y las
 * auditorias de Lighthouse. Escribir las reglas WCAG a mano no tendria sentido:
 * son cientos, cambian con las versiones del estandar, y la parte dificil no es
 * evaluarlas sino saber cuales aplican a cada nodo.
 *
 * Lo que si aporta esta clase: elegir el conjunto de reglas segun el ambiente,
 * dejar el resultado en el reporte aunque el caso pase, y devolver una estructura
 * propia en vez de la de la libreria, para que cambiar de motor no toque ni un
 * test.
 *
 * Que axe detecta y que no: encuentra lo que se puede decidir mirando el DOM
 * —contraste, textos alternativos, roles ARIA mal usados, orden de encabezados—
 * y ronda el 30% de los problemas reales de accesibilidad. Que el orden de
 * tabulacion tenga sentido, que un texto alternativo describa la imagen o que un
 * lector de pantalla se entienda, no. Un cero de axe no es una pagina accesible;
 * es una pagina sin los errores que una maquina puede ver sola.
 */
public final class AnalisisA11y {

    /** Reglas por defecto: WCAG 2.1 niveles A y AA, que es lo que suele exigirse. */
    private static final String TAGS_POR_DEFECTO = "wcag2a,wcag2aa,wcag21a,wcag21aa";

    private AnalisisA11y() {
    }

    private static List<String> tags() {
        return List.of(ConfigManager.get().get("a11y.tags", TAGS_POR_DEFECTO).split("\\s*,\\s*"));
    }

    /** Analiza la pagina entera. */
    public static List<ViolacionA11y> analizar() {
        return traducir(new AxeBuilder().withTags(tags()).analyze(DriverManager.get()));
    }

    /**
     * Analiza solo una parte de la pagina.
     *
     * Util cuando lo que se esta probando es un componente y el resto de la
     * pantalla ya tiene su propia linea base: acota el resultado a lo que el caso
     * realmente ejercita.
     */
    public static List<ViolacionA11y> analizar(By dentroDe) {
        WebElement raiz = DriverManager.get().findElement(dentroDe);
        return traducir(new AxeBuilder().withTags(tags()).analyze(DriverManager.get(), raiz));
    }

    private static List<ViolacionA11y> traducir(Results resultado) {
        if (resultado.isErrored()) {
            throw new AccesibilidadException(
                    "axe-core no pudo analizar la pagina: " + resultado.getErrorMessage());
        }
        return resultado.getViolations().stream()
                .map(AnalisisA11y::aViolacion)
                .sorted(Comparator.comparingInt(ViolacionA11y::gravedad)
                        .thenComparing(ViolacionA11y::regla))
                .toList();
    }

    private static ViolacionA11y aViolacion(Rule regla) {
        List<String> elementos = regla.getNodes().stream()
                .map(nodo -> String.valueOf(nodo.getTarget()))
                .toList();
        return new ViolacionA11y(regla.getId(), regla.getImpact(),
                regla.getHelp(), regla.getHelpUrl(), elementos);
    }

    // ------------------------------------------------------------------
    // Reporte
    // ------------------------------------------------------------------

    /**
     * Deja el analisis en el reporte, pase o falle el caso.
     *
     * Que quede siempre es deliberado: la linea base solo hace fallar lo que
     * empeoro, asi que sin esto las violaciones ya conocidas serian invisibles y
     * nadie las arreglaria nunca.
     */
    public static void reportar(String pagina, List<ViolacionA11y> violaciones) {
        if (violaciones.isEmpty()) {
            ExtentTestManager.ok("Accesibilidad en '" + pagina + "': sin violaciones");
            LogUtils.info("Accesibilidad en '" + pagina + "': sin violaciones");
            return;
        }

        String html = comoTabla(pagina, violaciones);
        ExtentTestManager.advertencia(html);
        AllureManager.adjuntarHtml("Accesibilidad: " + pagina, html);
        LogUtils.warn("Accesibilidad en '" + pagina + "': " + violaciones.size()
                + " reglas incumplidas -> " + violaciones);
    }

    private static String comoTabla(String pagina, List<ViolacionA11y> violaciones) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Accesibilidad en '").append(escapar(pagina)).append("'</b>")
          .append("<table style='width:100%;border-collapse:collapse'>")
          .append("<tr><th align='left'>Regla</th><th align='left'>Impacto</th>")
          .append("<th align='left'>Elementos</th><th align='left'>Que arreglar</th></tr>");
        for (ViolacionA11y v : violaciones) {
            sb.append("<tr>")
              .append("<td><a href='").append(escapar(v.ayudaUrl())).append("'>")
              .append(escapar(v.regla())).append("</a></td>")
              .append("<td>").append(escapar(v.impacto())).append("</td>")
              .append("<td>").append(v.cantidad()).append("</td>")
              .append("<td>").append(escapar(v.ayuda())).append("</td>")
              .append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String escapar(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
