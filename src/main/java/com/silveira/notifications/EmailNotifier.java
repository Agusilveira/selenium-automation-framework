package com.silveira.notifications;

import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Manda el resumen de la corrida por mail.
 *
 * Cuatro decisiones, todas contra el notificador tipico:
 *
 * <b>Las credenciales salen del entorno y de ningun otro lado.</b> No hay una
 * clave `mail.password` en el perfil ni un `get` de ConfigManager que pueda caer
 * en un archivo del repositorio. Si el framework no puede leer una contraseña de
 * un archivo versionado, nadie la puede filtrar ahi por accidente.
 *
 * <b>Sin configuracion no hace nada, y eso no es un error.</b> Un `git clone &&
 * mvn test` no tiene que romperse ni colgarse porque falte un servidor SMTP.
 *
 * <b>Por defecto avisa solo cuando algo fallo.</b> Una notificacion que llega
 * siempre deja de leerse en una semana, y entonces tampoco se lee la que importa.
 *
 * <b>Nunca hace fallar la suite.</b> Un error al notificar se registra y se
 * termina ahi: pintar de rojo una corrida verde porque el servidor de mail estaba
 * caido es informar peor que no informar. Por eso ademas hay timeouts explicitos:
 * un SMTP que no responde colgaria el build hasta el timeout del runner.
 */
public final class EmailNotifier {

    public enum Cuando { SIEMPRE, FALLOS, NUNCA }

    private static final int TIMEOUT_MS = 10_000;

    private EmailNotifier() {
    }

    /**
     * Datos del servidor. Es un valor y no lecturas sueltas del entorno para poder
     * apuntar el notificador a un SMTP de prueba y verificar que el mail sale.
     */
    public record ConfiguracionSmtp(String host,
                                    int puerto,
                                    String usuario,
                                    String password,
                                    String remitente,
                                    List<String> destinatarios,
                                    Cuando cuando) {

        /**
         * Autenticacion y TLS van juntas y dependen de si hay usuario.
         *
         * No es una concesion al test: un relay interno sin credenciales es un caso
         * real, y el servidor de prueba se comporta como uno.
         */
        public boolean requiereAutenticacion() {
            return usuario != null && !usuario.isBlank();
        }

        public static Optional<ConfiguracionSmtp> desdeElEntorno() {
            String host = env("MAIL_SMTP_HOST");
            String destinatarios = env("MAIL_DESTINATARIOS");
            if (host.isBlank() || destinatarios.isBlank()) return Optional.empty();

            String usuario = env("MAIL_USUARIO");
            String remitente = env("MAIL_REMITENTE");
            return Optional.of(new ConfiguracionSmtp(
                    host,
                    Integer.parseInt(env("MAIL_SMTP_PUERTO").isBlank() ? "587" : env("MAIL_SMTP_PUERTO")),
                    usuario,
                    env("MAIL_PASSWORD"),
                    remitente.isBlank() ? usuario : remitente,
                    Arrays.stream(destinatarios.split("\\s*,\\s*")).filter(d -> !d.isBlank()).toList(),
                    cuando(env("MAIL_CUANDO"))));
        }

        private static Cuando cuando(String valor) {
            if (valor.isBlank()) return Cuando.FALLOS;
            try {
                return Cuando.valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                LogUtils.warn("MAIL_CUANDO='" + valor + "' no es válido. Se usa FALLOS. "
                        + "Opciones: " + Arrays.toString(Cuando.values()));
                return Cuando.FALLOS;
            }
        }

        private static String env(String clave) {
            String valor = System.getenv(clave);
            return valor == null ? "" : valor.trim();
        }
    }

    // ------------------------------------------------------------------

    /**
     * Punto de entrada desde el listener: decide, envia, y pase lo que pase vuelve.
     */
    public static void notificar(ResumenDeCorrida resumen) {
        Optional<ConfiguracionSmtp> config = ConfiguracionSmtp.desdeElEntorno();
        if (config.isEmpty()) {
            LogUtils.debug("Notificaciones por mail desactivadas: falta MAIL_SMTP_HOST "
                    + "o MAIL_DESTINATARIOS en el entorno.");
            return;
        }
        notificar(resumen, config.get());
    }

    /**
     * Igual que el anterior con la configuración ya resuelta, y con la misma
     * garantía: no propaga nada. Es el método que verifica el test apuntándolo a un
     * servidor que no existe.
     */
    public static void notificar(ResumenDeCorrida resumen, ConfiguracionSmtp config) {
        try {
            if (enviar(resumen, config)) {
                LogUtils.info("Resumen de la corrida enviado a "
                        + String.join(", ", config.destinatarios()));
            }
        } catch (Exception | LinkageError e) {
            // LinkageError y no solo RuntimeException por experiencia propia: el
            // primer fallo de esta clase fue un NoClassDefFoundError por dos
            // implementaciones de mail en el classpath, y se llevaba puesta la
            // suite entera. Un notificador que promete no afectar la corrida tiene
            // que cumplirlo tambien cuando el problema es el classpath.
            LogUtils.error("No se pudo enviar la notificación por mail. La corrida no se "
                    + "ve afectada: " + e, e);
        }
    }

    /** Devuelve si llego a mandar algo. False significa que la politica dijo que no. */
    public static boolean enviar(ResumenDeCorrida resumen, ConfiguracionSmtp config) {
        if (!corresponde(resumen, config.cuando())) {
            LogUtils.debug("No se notifica: la política es " + config.cuando()
                    + " y la corrida terminó con " + resumen.fallados() + " fallos.");
            return false;
        }

        try {
            MimeMessage mensaje = new MimeMessage(sesion(config));
            mensaje.setFrom(new InternetAddress(config.remitente()));
            for (String destinatario : config.destinatarios()) {
                mensaje.addRecipient(Message.RecipientType.TO, new InternetAddress(destinatario));
            }
            mensaje.setSubject(resumen.asunto(), "UTF-8");
            mensaje.setContent(resumen.comoHtml(), "text/html; charset=UTF-8");
            Transport.send(mensaje);
            return true;
        } catch (MessagingException e) {
            throw new FrameworkException("Falló el envío del mail a " + config.host()
                    + ":" + config.puerto(), e);
        }
    }

    private static boolean corresponde(ResumenDeCorrida resumen, Cuando cuando) {
        return switch (cuando) {
            case SIEMPRE -> true;
            case FALLOS  -> resumen.hayFallos();
            case NUNCA   -> false;
        };
    }

    /**
     * Dos implementaciones de mail conviven en el classpath, y hay que elegir.
     *
     * `json-schema-validator` arrastra `com.sun.mail:mailapi`, de la era
     * `javax.mail`, y no se puede sacar: sin el jar, cualquier validacion contra
     * JSON Schema falla. El problema es que su META-INF/mailcap declara manejadores
     * `com.sun.mail.handlers.*`, y `jakarta.activation` lee todos los mailcap del
     * classpath: al armar un mail encuentra el manejador javax de text/html,
     * intenta cargarlo y explota con NoClassDefFoundError.
     *
     * Lo registrado por programa tiene prioridad sobre lo leido de archivos, asi
     * que basta con nombrar el manejador correcto. Es una linea por el tipo de
     * contenido que efectivamente se manda.
     */
    private static void resolverElChoqueDeImplementaciones() {
        MailcapCommandMap mapa = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mapa.addMailcap("text/html;; x-java-content-handler=org.eclipse.angus.mail.handlers.text_html");
        CommandMap.setDefaultCommandMap(mapa);
    }

    private static Session sesion(ConfiguracionSmtp config) {
        resolverElChoqueDeImplementaciones();
        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.puerto()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));

        if (!config.requiereAutenticacion()) {
            return Session.getInstance(props);
        }

        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.usuario(), config.password());
            }
        });
    }
}
