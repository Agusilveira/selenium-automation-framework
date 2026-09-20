package com.silveira.notifications;

import com.silveira.config.ConfigManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.helpers.FileHelper;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Plantilla del asunto si nadie define la suya. */
    public static final String ASUNTO_POR_DEFECTO =
            "[{{estado}}] {{suite}} en {{entorno}} — {{pasados}}/{{total}} pasaron";

    private static final String PLANTILLA_EMPAQUETADA = "/templates/mail-resumen.html";

    public String asunto() {
        return Plantilla.renderTexto(
                ConfigManager.get().get("mail.asunto", ASUNTO_POR_DEFECTO), valores());
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
        return comoHtml(plantilla());
    }

    /** Renderiza contra una plantilla dada. Es la puerta que usan los tests. */
    public String comoHtml(String plantilla) {
        return Plantilla.renderHtml(plantilla, valores());
    }

    /**
     * Los parámetros que ve la plantilla.
     *
     * Es el contrato con quien escriba la suya: agregar una clave acá es agregar
     * un parámetro disponible, y no hay ningún otro lugar que tocar.
     */
    public Map<String, Object> valores() {
        List<Map<String, Object>> casos = fallos.stream()
                .map(f -> Map.<String, Object>of(
                        "clase", f.clase(),
                        "metodo", f.metodo(),
                        "nombreCompleto", f.nombreCompleto(),
                        "mensaje", f.mensaje()))
                .toList();

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("suite", suite);
        valores.put("entorno", entorno);
        valores.put("navegador", navegador);
        valores.put("duracion", duracionLegible());
        valores.put("estado", hayFallos() ? "FALLÓ" : "OK");
        valores.put("color", hayFallos() ? "#c0392b" : "#27ae60");
        valores.put("pasados", pasados);
        valores.put("fallados", fallados);
        valores.put("omitidos", omitidos);
        valores.put("total", total());
        valores.put("hayFallos", hayFallos());
        valores.put("fallos", casos);
        valores.put("urlReporte", urlDelReporte);
        return valores;
    }

    /**
     * La plantilla configurada, o la que viene con el framework.
     *
     * Un archivo propio gana sobre la empaquetada, y si esa ruta no existe se
     * falla diciéndolo: caer silenciosamente en la de por defecto haría que un
     * error de tipeo en la ruta se descubra recién al mirar un mail que no se
     * parece al que se esperaba.
     */
    private static String plantilla() {
        String ruta = ConfigManager.get().get("mail.plantilla", "");
        if (!ruta.isBlank()) {
            return FileHelper.leerTexto(ruta);
        }
        try (InputStream in = ResumenDeCorrida.class.getResourceAsStream(PLANTILLA_EMPAQUETADA)) {
            if (in == null) {
                throw new FrameworkException("Falta la plantilla empaquetada " + PLANTILLA_EMPAQUETADA);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer " + PLANTILLA_EMPAQUETADA, e);
        }
    }

    public String duracionLegible() {
        long minutos = duracion.toMinutes();
        long segundos = duracion.minusMinutes(minutos).toSeconds();
        return minutos > 0 ? minutos + " min " + segundos + " s" : segundos + " s";
    }

}
