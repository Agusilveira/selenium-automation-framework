package com.silveira.notifications;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.notifications.EmailNotifier.ConfiguracionSmtp;
import com.silveira.notifications.EmailNotifier.Cuando;
import com.silveira.notifications.ResumenDeCorrida.CasoFallado;
import jakarta.mail.internet.MimeMessage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica que el mail sale de verdad, contra un servidor SMTP en memoria.
 *
 * Se podría probar solo el HTML del resumen y dar por hecho el envío, pero
 * entonces el notificador entraría en la misma categoría que el `ContractGuard`
 * antes de sabotearlo a propósito: un mecanismo que nunca se ejecutó completo.
 * GreenMail levanta un SMTP real en un puerto libre, así que lo que se verifica
 * es el camino entero —conexión, sobre, asunto, cuerpo— sin una casilla ni red.
 */
public class EmailNotifierTest {

    private GreenMail servidor;
    private int puerto;

    @BeforeClass
    public void levantarElServidor() {
        puerto = puertoLibre();
        servidor = new GreenMail(new ServerSetup(puerto, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        servidor.start();
    }

    @AfterClass(alwaysRun = true)
    public void bajarElServidor() {
        if (servidor != null) servidor.stop();
    }

    /** Un puerto fijo choca con cualquier otra cosa en el runner. */
    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo reservar un puerto para el SMTP de prueba", e);
        }
    }

    private ConfiguracionSmtp configuracion(Cuando cuando) {
        return new ConfiguracionSmtp("127.0.0.1", puerto, "", "",
                "framework@silveira.test", List.of("equipo@silveira.test"), cuando);
    }

    private ResumenDeCorrida conFallos() {
        return new ResumenDeCorrida("Regresion", "ci", "chrome", 56, 2, 0,
                Duration.ofSeconds(94),
                List.of(new CasoFallado("CheckoutTest", "compraCompleta", "no apareció el botón de pago"),
                        new CasoFallado("LoginTest", "usuarioBloqueado", "el mensaje no coincide")),
                "https://agusilveira.github.io/reporte");
    }

    private ResumenDeCorrida todoVerde() {
        return new ResumenDeCorrida("Regresion", "ci", "chrome", 58, 0, 0,
                Duration.ofSeconds(88), List.of(), "");
    }

    // ------------------------------------------------------------------

    @Test(groups = "unit", description = "El mail llega con el asunto y el detalle de los fallos")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void elMailLlegaConElDetalleDeLosFallos() throws Exception {
        assertThat(EmailNotifier.enviar(conFallos(), configuracion(Cuando.FALLOS))).isTrue();
        assertThat(servidor.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage[] recibidos = servidor.getReceivedMessages();
        assertThat(recibidos).hasSize(1);

        MimeMessage mail = recibidos[0];
        assertThat(mail.getSubject())
                .as("el asunto tiene que decir si hay que abrirlo, sin abrirlo")
                .contains("FALLÓ", "Regresion", "ci", "56/58");
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("equipo@silveira.test");

        // getContent y no getBody: el cuerpo viaja en quoted-printable, asi que
        // crudo dice "fall=C3=B3" y ningun contains funcionaria.
        String cuerpo = String.valueOf(mail.getContent());
        assertThat(cuerpo)
                .as("los casos fallados y su motivo van en el cuerpo, no en un adjunto")
                .contains("CheckoutTest.compraCompleta")
                .contains("no apareció el botón de pago")
                .contains("LoginTest.usuarioBloqueado")
                .contains("https://agusilveira.github.io/reporte");
    }

    @Test(groups = "unit", description = "Con la politica por defecto una corrida verde no notifica",
          dependsOnMethods = "elMailLlegaConElDetalleDeLosFallos")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void unaCorridaVerdeNoNotificaPorDefecto() {
        int antes = servidor.getReceivedMessages().length;

        assertThat(EmailNotifier.enviar(todoVerde(), configuracion(Cuando.FALLOS)))
                .as("una notificación que llega siempre se vuelve ruido y se ignora")
                .isFalse();

        assertThat(servidor.getReceivedMessages()).hasSize(antes);
    }

    @Test(groups = "unit", description = "La politica SIEMPRE tambien avisa cuando todo paso",
          dependsOnMethods = "unaCorridaVerdeNoNotificaPorDefecto")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void conPoliticaSiempreTambienAvisaEnVerde() throws Exception {
        int antes = servidor.getReceivedMessages().length;

        assertThat(EmailNotifier.enviar(todoVerde(), configuracion(Cuando.SIEMPRE))).isTrue();
        assertThat(servidor.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage[] recibidos = servidor.getReceivedMessages();
        assertThat(recibidos).hasSizeGreaterThan(antes);
        assertThat(recibidos[recibidos.length - 1].getSubject()).contains("[OK]");
    }

    @Test(groups = "unit", description = "Un servidor de mail caido no hace fallar la corrida")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"notificaciones"})
    public void unNotificadorCaidoNoRompeLaCorrida() {
        ConfiguracionSmtp inalcanzable = new ConfiguracionSmtp(
                "127.0.0.1", puertoLibre(), "", "", "framework@silveira.test",
                List.of("equipo@silveira.test"), Cuando.SIEMPRE);

        // La garantia que importa: pintar de rojo una corrida verde porque el
        // servidor de mail estaba caido es informar peor que no informar.
        assertThatCode(() -> EmailNotifier.notificar(conFallos(), inalcanzable))
                .doesNotThrowAnyException();
    }
}
