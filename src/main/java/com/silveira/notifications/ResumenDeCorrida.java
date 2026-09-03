package com.silveira.notifications;

import com.silveira.config.ConfigManager;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Que paso en la corrida, sin saber nada de como se va a avisar.
 *
 * Esta separacion es la diferencia entre esto y el notificador tipico, que arma
 * el texto del mail mientras recorre los resultados de TestNG. Ahi el "que paso"
 * y el "por donde se avisa" quedan pegados: sumar Telegram obliga a repetir el
 * recorrido, y probar el formato del mensaje obliga a levantar un servidor SMTP.
 *
 * Aca el resumen es un valor. Se construye una vez desde la suite, se puede
 * imprimir en una aserción, y cada canal es una funcion que lo recibe.
 */
public record ResumenDeCorrida(String suite,
                               String entorno,
                               String navegador,
                               int pasados,
                               int fallados,
                               int omitidos,
                               Duration duracion,
                               List<CasoFallado> fallos,
                               String urlDelReporte) {

    /** Lo minimo para saber que se rompio sin abrir nada. */
    public record CasoFallado(String clase, String metodo, String mensaje) {
        public String nombreCompleto() {
            return clase + "." + metodo;
        }
    }

    public int total() {
        return pasados + fallados + omitidos;
    }

    public boolean hayFallos() {
        return fallados > 0;
    }

    public String asunto() {
        String estado = hayFallos() ? "FALLÓ" : "OK";
        return "[" + estado + "] " + suite + " en " + entorno
                + " — " + pasados + "/" + total() + " pasaron";
    }

    // ------------------------------------------------------------------

    public static ResumenDeCorrida de(ISuite suite) {
        int pasados = 0;
        int fallados = 0;
        int omitidos = 0;
        long milisegundos = 0;
        List<CasoFallado> fallos = new ArrayList<>();

        for (ISuiteResult resultado : suite.getResults().values()) {
            ITestContext contexto = resultado.getTestContext();
            pasados  += contexto.getPassedTests().size();
            fallados += contexto.getFailedTests().size();
            omitidos += contexto.getSkippedTests().size();
            milisegundos += contexto.getEndDate().getTime() - contexto.getStartDate().getTime();

            for (ITestResult caso : contexto.getFailedTests().getAllResults()) {
                fallos.add(new CasoFallado(
                        caso.getTestClass().getRealClass().getSimpleName(),
                        caso.getMethod().getMethodName(),
                        mensajeDe(caso)));
            }
        }

        ConfigManager config = ConfigManager.get();
        return new ResumenDeCorrida(suite.getName(), ConfigManager.perfilActivo(),
                config.get("browser", "?"), pasados, fallados, omitidos,
                Duration.ofMillis(milisegundos), fallos,
                config.get("reporte.url", ""));
    }

    /**
     * Primera linea del error y nada mas.
     *
     * Un stack trace completo por caso hace que el mail no se lea en el telefono,
     * que es donde se lo lee. El stack esta en el reporte, a un click.
     */
    private static String mensajeDe(ITestResult caso) {
        Throwable causa = caso.getThrowable();
        if (causa == null) return "sin mensaje";
        String mensaje = causa.getMessage();
        if (mensaje == null || mensaje.isBlank()) return causa.getClass().getSimpleName();
        return mensaje.lines().findFirst().orElse(mensaje).trim();
    }

    // ------------------------------------------------------------------
    // Formatos
    // ------------------------------------------------------------------

    /**
     * El cuerpo del mail.
     *
     * Se manda el detalle en vez de adjuntar el reporte de Extent a proposito. El
     * adjunto pesa varios megas, muchos servidores lo bloquean por ser HTML, y
     * obliga a bajarlo para saber si hace falta mirarlo. Con los casos fallados y
     * su primer mensaje adentro del mail, la decision de abrir el reporte o no se
     * toma leyendo la notificacion.
     */
    public String comoHtml() {
        String color = hayFallos() ? "#c0392b" : "#27ae60";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:system-ui,sans-serif;font-size:14px\">")
          .append("<h2 style=\"color:").append(color).append(";margin-bottom:4px\">")
          .append(escapar(suite)).append(hayFallos() ? ": falló" : ": todo verde").append("</h2>")
          .append("<p style=\"color:#555;margin-top:0\">")
          .append(escapar(entorno)).append(" · ").append(escapar(navegador))
          .append(" · ").append(duracionLegible()).append("</p>")
          .append("<p><b>").append(pasados).append("</b> pasaron · <b>")
          .append(fallados).append("</b> fallaron · <b>")
          .append(omitidos).append("</b> omitidos</p>");

        if (!fallos.isEmpty()) {
            sb.append("<h3>Casos fallados</h3><ul>");
            for (CasoFallado fallo : fallos) {
                sb.append("<li><b>").append(escapar(fallo.nombreCompleto())).append("</b><br>")
                  .append("<span style=\"color:#555\">").append(escapar(fallo.mensaje()))
                  .append("</span></li>");
            }
            sb.append("</ul>");
        }

        if (!urlDelReporte.isBlank()) {
            sb.append("<p><a href=\"").append(escapar(urlDelReporte))
              .append("\">Ver el reporte completo</a></p>");
        }
        return sb.append("</div>").toString();
    }

    public String duracionLegible() {
        long minutos = duracion.toMinutes();
        long segundos = duracion.minusMinutes(minutos).toSeconds();
        return minutos > 0 ? minutos + " min " + segundos + " s" : segundos + " s";
    }

    private static String escapar(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
