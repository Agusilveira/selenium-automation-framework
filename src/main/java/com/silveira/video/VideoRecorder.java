package com.silveira.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.silveira.config.ConfigManager;
import com.silveira.config.FrameworkConstants;
import com.silveira.driver.DriverManager;
import com.silveira.utils.DateUtils;
import com.silveira.utils.LogUtils;
import com.silveira.video.CodificadorMp4.Cuadro;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Graba en video lo que pasa en el navegador, sin Grid y sin ffmpeg.
 *
 * Usa el screencast de CDP: el navegador empuja un cuadro cada vez que la página
 * cambia. Eso es lo que lo hace viable donde las alternativas no lo son:
 *
 * <b>No saca capturas desde otro hilo.</b> Un grabador que llama a
 * `getScreenshotAs` en un hilo paralelo mete comandos concurrentes en una sesión
 * de WebDriver, que procesa uno por vez: en el mejor caso enlentece el caso, en el
 * peor lo rompe con un error que no tiene nada que ver con lo que se estaba
 * probando. Acá el tráfico va por una conexión aparte y no toca la sesión.
 *
 * <b>Funciona headless.</b> Los grabadores de escritorio necesitan una pantalla
 * real, así que no sirven en CI, que es donde más falta hace el video.
 *
 * <b>Chrome y Edge, no Firefox.</b> Firefox abandonó CDP y su reemplazo todavía
 * no tiene screencast. En Firefox esto no graba y lo dice; para cubrirlo está el
 * Grid, que filma el display del nodo desde afuera.
 *
 * El transporte es {@link ConexionCdp} y no la API de DevTools de Selenium, por un
 * motivo que está explicado en detalle ahí: esa API se apaga sola cada vez que
 * Chrome se actualiza.
 *
 * Por defecto el video se conserva solo si el caso falló. Guardar el de cada caso
 * que pasó son cientos de archivos que nadie va a mirar y un artefacto de CI que
 * pesa de más.
 */
public final class VideoRecorder {

    public enum Cuando { SIEMPRE, FALLOS, NUNCA }

    private static final ThreadLocal<Grabacion> ACTIVA = new ThreadLocal<>();

    private VideoRecorder() {
    }

    private static final class Grabacion {
        private final String nombre;
        private final Instant inicio = Instant.now();
        private final List<Cuadro> cuadros = Collections.synchronizedList(new ArrayList<>());
        private ConexionCdp conexion;
        private volatile boolean conservar;
        private volatile boolean lleno;

        private Grabacion(String nombre) {
            this.nombre = nombre;
        }
    }

    // ------------------------------------------------------------------

    public static Cuando politica() {
        String valor = ConfigManager.get().get("video.cuando", Cuando.FALLOS.name()).trim();
        try {
            return Cuando.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            LogUtils.warn("video.cuando='" + valor + "' no es válido, se usa FALLOS.");
            return Cuando.FALLOS;
        }
    }

    public static boolean grabando() {
        return ACTIVA.get() != null;
    }

    /**
     * Empieza a grabar el caso. Si el navegador no puede, lo dice y sigue.
     *
     * Nunca lanza: que no haya video no puede ser el motivo por el que falle un
     * caso que probaba otra cosa.
     */
    public static void iniciar(String nombre) {
        if (politica() == Cuando.NUNCA || grabando()) return;
        // Las suites de API y de base de datos no abren navegador. Preguntar antes
        // evita un warning por cada caso de algo que nunca iba a grabarse.
        if (!DriverManager.hayDriver()) return;

        try {
            Grabacion grabacion = new Grabacion(nombre);
            Optional<ConexionCdp> conexion =
                    ConexionCdp.aLaPestanaDe(DriverManager.get(), evento -> recibir(grabacion, evento));

            if (conexion.isEmpty()) {
                LogUtils.debug("Sin grabación de video: " + ConfigManager.get().get("browser", "?")
                        + " no habla CDP. Para grabar Firefox está el Grid.");
                return;
            }

            grabacion.conexion = conexion.get();
            grabacion.conexion.enviar("Page.startScreencast", Map.of(
                    "format", "jpeg",
                    "quality", ConfigManager.get().getInt("video.calidad", 60),
                    "maxWidth", ConfigManager.get().getInt("video.ancho", 640),
                    "maxHeight", ConfigManager.get().getInt("video.alto", 512),
                    "everyNthFrame", 1));

            ACTIVA.set(grabacion);
            LogUtils.debug("Grabando video de " + nombre);

        } catch (Exception | LinkageError e) {
            LogUtils.warn("No se pudo iniciar la grabación de " + nombre + ": " + e);
            ACTIVA.remove();
        }
    }

    /** Marca que este video hay que guardarlo aunque la política sea solo-fallos. */
    public static void marcarParaConservar() {
        Grabacion grabacion = ACTIVA.get();
        if (grabacion != null) grabacion.conservar = true;
    }

    /**
     * Corta la grabación y escribe el archivo si corresponde.
     *
     * Tiene que llamarse con el navegador todavía abierto: detener el screencast
     * es un comando más sobre la misma pestaña.
     */
    public static Optional<Path> detener() {
        Grabacion grabacion = ACTIVA.get();
        if (grabacion == null) return Optional.empty();
        ACTIVA.remove();

        try {
            grabacion.conexion.enviar("Page.stopScreencast", Map.of());
        } catch (Exception | LinkageError e) {
            LogUtils.debug("El screencast ya no respondía al detenerlo: " + e);
        }
        grabacion.conexion.close();

        List<Cuadro> cuadros = List.copyOf(grabacion.cuadros);
        if (!corresponde(grabacion) || cuadros.isEmpty()) {
            if (cuadros.isEmpty()) {
                LogUtils.debug("La grabación de " + grabacion.nombre + " quedó vacía");
            }
            return Optional.empty();
        }

        try {
            Path destino = Path.of(FrameworkConstants.RUTA_VIDEOS,
                    grabacion.nombre + "-" + DateUtils.timestampParaArchivo() + ".mp4");
            CodificadorMp4.escribir(destino, cuadros, fps());
            LogUtils.info("Video de " + grabacion.nombre + " en " + destino
                    + " (" + cuadros.size() + " cuadros)");
            return Optional.of(destino);

        } catch (Exception | LinkageError e) {
            LogUtils.warn("No se pudo escribir el video de " + grabacion.nombre + ": " + e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------

    private static boolean corresponde(Grabacion grabacion) {
        return politica() == Cuando.SIEMPRE || grabacion.conservar;
    }

    private static void recibir(Grabacion grabacion, JsonNode evento) {
        if (!"Page.screencastFrame".equals(evento.path("method").asText())) return;

        try {
            JsonNode parametros = evento.path("params");
            // El acuse es control de flujo: sin él, el navegador deja de mandar
            // cuadros después del primero.
            grabacion.conexion.enviar("Page.screencastFrameAck",
                    Map.of("sessionId", parametros.path("sessionId").asInt()));

            if (grabacion.lleno) return;
            if (grabacion.cuadros.size() >= maximoDeCuadros()) {
                grabacion.lleno = true;
                LogUtils.warn("La grabación de " + grabacion.nombre + " llegó al máximo de "
                        + maximoDeCuadros() + " cuadros. El resto del caso no queda filmado.");
                return;
            }

            byte[] jpeg = Base64.getDecoder().decode(parametros.path("data").asText());
            grabacion.cuadros.add(
                    new Cuadro(jpeg, Duration.between(grabacion.inicio, Instant.now()).toMillis()));

        } catch (RuntimeException e) {
            LogUtils.debug("Se descartó un cuadro de video: " + e);
        }
    }

    private static int fps() {
        return Math.max(1, ConfigManager.get().getInt("video.fps", 4));
    }

    private static int maximoDeCuadros() {
        return ConfigManager.get().getInt("video.max.cuadros", 900);
    }
}
