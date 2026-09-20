package com.silveira.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Habla CDP directamente con el navegador, sin pasar por las clases de Selenium.
 *
 * <b>Por qué no usar `driver.getDevTools()`.</b> Esa API es cómoda pero viene en
 * paquetes atados a la versión del navegador: `selenium-devtools-v137` y
 * compañía. Selenium 4.33 trae hasta la 137; el Chrome de esta máquina es la 153.
 * Dieciséis versiones de diferencia, y Selenium devuelve una implementación
 * no-op: la grabación no arranca y avisa, pero no graba.
 *
 * Actualizar Selenium lo arregla por un rato. Chrome saca una versión mayor cada
 * cuatro semanas, así que el arreglo dura hasta la próxima actualización
 * automática del navegador, y entonces el video deja de existir sin que nadie
 * toque nada. Una función que se apaga sola y en silencio es peor que no tenerla:
 * el día que hace falta el video, no está, y nadie sabe desde cuándo.
 *
 * CDP en crudo no tiene ese problema. Los nombres de los comandos que se usan acá
 * —`Page.startScreencast` y su acuse— llevan años estables, y el transporte es
 * una websocket de la biblioteca estándar de Java. Nada que actualizar.
 */
final class ConexionCdp implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration ESPERA_CONEXION = Duration.ofSeconds(10);

    private final WebSocket socket;
    private final AtomicInteger siguienteId = new AtomicInteger(1);

    private ConexionCdp(WebSocket socket) {
        this.socket = socket;
    }

    /**
     * Se conecta a la pestaña que maneja el driver, si el navegador lo permite.
     *
     * Vacío significa "este navegador no habla CDP", que es el caso de Firefox y
     * no es un error: el llamador simplemente no graba.
     */
    static Optional<ConexionCdp> aLaPestanaDe(WebDriver driver, Consumer<JsonNode> alRecibirEvento) {
        Optional<String> direccion = direccionDeDepuracion(driver);
        if (direccion.isEmpty()) return Optional.empty();

        try {
            String url = urlDeLaPestana(direccion.get());
            WebSocket socket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(ESPERA_CONEXION)
                    .buildAsync(URI.create(url), new Receptor(alRecibirEvento))
                    .get(ESPERA_CONEXION.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            return Optional.of(new ConexionCdp(socket));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            LogUtils.debug("No se pudo abrir la conexión CDP: " + e);
            return Optional.empty();
        }
    }

    /**
     * El puerto de depuración que abrió el driver.
     *
     * Chrome y Edge lo publican en sus propias capabilities; el nombre de la clave
     * es lo único que cambia entre los dos.
     */
    private static Optional<String> direccionDeDepuracion(WebDriver driver) {
        if (!(driver instanceof HasCapabilities conCapabilities)) return Optional.empty();

        for (String clave : new String[]{"goog:chromeOptions", "ms:edgeOptions"}) {
            Object opciones = conCapabilities.getCapabilities().getCapability(clave);
            if (opciones instanceof Map<?, ?> mapa) {
                Object direccion = mapa.get("debuggerAddress");
                if (direccion != null && !String.valueOf(direccion).isBlank()) {
                    return Optional.of(String.valueOf(direccion));
                }
            }
        }
        return Optional.empty();
    }

    /** El endpoint del navegador lista sus targets; el que interesa es la página. */
    private static String urlDeLaPestana(String direccion) throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://" + direccion + "/json/list"))
                        .timeout(ESPERA_CONEXION).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        for (JsonNode target : JSON.readTree(respuesta.body())) {
            if ("page".equals(target.path("type").asText())) {
                return target.path("webSocketDebuggerUrl").asText();
            }
        }
        throw new FrameworkException("El navegador en " + direccion + " no expone ninguna pestaña");
    }

    // ------------------------------------------------------------------

    void enviar(String metodo, Map<String, Object> parametros) {
        try {
            String mensaje = JSON.writeValueAsString(Map.of(
                    "id", siguienteId.getAndIncrement(),
                    "method", metodo,
                    "params", parametros));
            // sendText es asincrono: se espera el envio para no encimar mensajes,
            // que en una websocket es un error de protocolo, no una carrera benigna.
            socket.sendText(mensaje, true).join();
        } catch (Exception e) {
            throw new FrameworkException("Falló el comando CDP " + metodo, e);
        }
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "fin").join();
        } catch (Exception e) {
            LogUtils.debug("La conexión CDP ya estaba cerrada: " + e);
        }
    }

    /**
     * Junta los mensajes que llegan partidos.
     *
     * Un cuadro de video son decenas de kilobytes en base64, así que la websocket
     * lo entrega en varios pedazos. Procesar cada pedazo por separado da JSON
     * inválido, y es el tipo de error que aparece solo con imágenes grandes.
     */
    private static final class Receptor implements WebSocket.Listener {

        private final StringBuilder acumulado = new StringBuilder();
        private final Consumer<JsonNode> alRecibirEvento;

        private Receptor(Consumer<JsonNode> alRecibirEvento) {
            this.alRecibirEvento = alRecibirEvento;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence datos, boolean ultimo) {
            acumulado.append(datos);
            if (ultimo) {
                String mensaje = acumulado.toString();
                acumulado.setLength(0);
                try {
                    JsonNode nodo = JSON.readTree(mensaje);
                    if (nodo.has("method")) alRecibirEvento.accept(nodo);
                } catch (Exception e) {
                    LogUtils.debug("Mensaje CDP ilegible: " + e);
                }
            }
            socket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            LogUtils.debug("Error en la conexión CDP: " + error);
        }
    }
}
