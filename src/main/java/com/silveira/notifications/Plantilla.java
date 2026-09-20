package com.silveira.notifications;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sustitución de parámetros en una plantilla, deliberadamente mínima.
 *
 * Soporta tres cosas y ninguna más:
 *
 * <pre>
 *   {{clave}}                    el valor, escapado si la salida es HTML
 *   {{#lista}} ... {{/lista}}    repite el bloque por cada elemento
 *   {{#bandera}} ... {{/bandera}}  incluye el bloque si el valor es verdadero
 *   {{^bandera}} ... {{/bandera}}  incluye el bloque si NO lo es
 * </pre>
 *
 * Las secciones se pueden anidar mientras no se repita el nombre en dos niveles,
 * que es todo lo que hace falta para el cuerpo de un mail.
 *
 * <b>Por qué no una librería.</b> Traer Mustache o Freemarker para esto son
 * cientos de kilobytes y una sintaxis entera que aprender, a cambio de nada que
 * este archivo no haga. Si algún día hace falta un condicional con comparación, un
 * filtro o un bucle dentro de un bucle, la respuesta correcta no es agrandar esto:
 * es reemplazarlo por Mustache, que para entonces ya se habrá ganado el lugar.
 *
 * Lo que sí hace bien es escapar. Un nombre de test con un `<` adentro rompe el
 * HTML del mail, y peor: el contenido viene de mensajes de error, que es texto que
 * nadie controla.
 */
public final class Plantilla {

    /** {{#nombre}}cuerpo{{/nombre}} o su versión negada con ^. */
    private static final Pattern SECCION =
            Pattern.compile("\\{\\{([#^])(\\w+)}}(.*?)\\{\\{/\\2}}", Pattern.DOTALL);

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)}}");

    private Plantilla() {
    }

    /** Para el cuerpo del mail: los valores se escapan como HTML. */
    public static String renderHtml(String plantilla, Map<String, Object> valores) {
        return render(plantilla, valores, true);
    }

    /** Para el asunto y cualquier otra salida de texto plano. */
    public static String renderTexto(String plantilla, Map<String, Object> valores) {
        return render(plantilla, valores, false);
    }

    private static String render(String plantilla, Map<String, Object> valores, boolean html) {
        return variables(secciones(plantilla, valores, html), valores, html);
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String secciones(String plantilla, Map<String, Object> valores, boolean html) {
        Matcher m = SECCION.matcher(plantilla);
        StringBuilder salida = new StringBuilder();

        while (m.find()) {
            boolean negada = "^".equals(m.group(1));
            Object valor = valores.get(m.group(2));
            String cuerpo = m.group(3);
            String reemplazo;

            if (valor instanceof List<?> lista) {
                // Una lista repite el bloque; negada, aparece solo si está vacía.
                reemplazo = negada && lista.isEmpty() ? render(cuerpo, valores, html)
                          : negada ? ""
                          : lista.stream()
                                 .map(item -> render(cuerpo, (Map<String, Object>) item, html))
                                 .reduce("", String::concat);
            } else {
                reemplazo = esVerdadero(valor) != negada ? render(cuerpo, valores, html) : "";
            }
            m.appendReplacement(salida, Matcher.quoteReplacement(reemplazo));
        }
        return m.appendTail(salida).toString();
    }

    private static String variables(String plantilla, Map<String, Object> valores, boolean html) {
        Matcher m = VARIABLE.matcher(plantilla);
        StringBuilder salida = new StringBuilder();

        while (m.find()) {
            Object valor = valores.get(m.group(1));
            // Una clave que no existe se deja como está en vez de quedar vacía: si
            // alguien escribió mal un parámetro, que se vea en el mail y no que
            // desaparezca el dato sin dejar rastro.
            String texto = valor == null ? m.group() : String.valueOf(valor);
            m.appendReplacement(salida,
                    Matcher.quoteReplacement(html && valor != null ? escapar(texto) : texto));
        }
        return m.appendTail(salida).toString();
    }

    private static boolean esVerdadero(Object valor) {
        if (valor == null) return false;
        if (valor instanceof Boolean b) return b;
        if (valor instanceof Number n) return n.doubleValue() != 0;
        return !String.valueOf(valor).isBlank();
    }

    public static String escapar(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
