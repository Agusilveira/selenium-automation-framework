package com.silveira.video;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.video.CodificadorMp4.Cuadro;
import org.jcodec.api.awt.AWTFrameGrab;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el MP4 que sale se puede volver a abrir.
 *
 * Un grabador que escribe un archivo corrupto se descubre el día que alguien
 * necesita el video, que es el peor día posible. Acá el archivo se decodifica de
 * vuelta y se le miran los cuadros, así que "grabó" significa "se puede ver".
 *
 * Sin navegador a propósito: estos casos prueban la codificación y la
 * reconstrucción del tiempo, que es la lógica que puede estar mal. Que DevTools
 * entregue cuadros es otra cosa, y se prueba corriendo un caso de verdad.
 */
public class CodificadorMp4Test {

    private static final int ANCHO = 320;
    private static final int ALTO = 240;

    private Path destino;

    @AfterClass(alwaysRun = true)
    public void borrarElVideo() throws IOException {
        if (destino != null) Files.deleteIfExists(destino);
    }

    private static byte[] jpegDeColor(Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ANCHO, ALTO);
        g.dispose();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    /** H.264 es con pérdida: el color vuelve parecido, no idéntico. */
    private static void assertSeParecaA(Color esperado, int rgb, String cual) {
        Color obtenido = new Color(rgb);
        assertThat(Math.abs(obtenido.getRed() - esperado.getRed())
                 + Math.abs(obtenido.getGreen() - esperado.getGreen())
                 + Math.abs(obtenido.getBlue() - esperado.getBlue()))
                .as(cual + " debería parecerse a " + esperado + " y vino " + obtenido)
                .isLessThan(90);
    }

    // ------------------------------------------------------------------

    @Test(groups = "unit",
          description = "El video se escribe, se puede decodificar y respeta el tiempo real")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"video"})
    public void elVideoSeEscribeYSePuedeVolverALeer() throws Exception {
        destino = Path.of("target", "video-de-prueba.mp4");
        Files.deleteIfExists(destino);

        // Rojo desde el arranque, verde recién a los 2 segundos. A 4 cuadros por
        // segundo eso son 9 cuadros: los 8 primeros rojos y el último verde.
        List<Cuadro> cuadros = List.of(
                new Cuadro(jpegDeColor(Color.RED), 0),
                new Cuadro(jpegDeColor(Color.GREEN), 2000));

        CodificadorMp4.escribir(destino, cuadros, 4);

        assertThat(destino).exists();
        assertThat(Files.size(destino)).isGreaterThan(500);

        BufferedImage primero = AWTFrameGrab.getFrame(destino.toFile(), 0);
        assertThat(primero.getWidth()).isEqualTo(ANCHO);
        assertThat(primero.getHeight()).isEqualTo(ALTO);
        assertSeParecaA(Color.RED, primero.getRGB(ANCHO / 2, ALTO / 2), "el primer cuadro");

        // A un segundo y medio todavía no pasó nada: el rojo sigue en pantalla.
        // Si el tiempo no se reconstruyera, acá ya estaría el verde.
        assertSeParecaA(Color.RED,
                AWTFrameGrab.getFrame(destino.toFile(), 6).getRGB(ANCHO / 2, ALTO / 2),
                "el cuadro de 1,5 s");

        assertSeParecaA(Color.GREEN,
                AWTFrameGrab.getFrame(destino.toFile(), 8).getRGB(ANCHO / 2, ALTO / 2),
                "el último cuadro");
    }

    @Test(groups = "unit", description = "Un solo cuadro tambien produce un video valido")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"video"})
    public void unSoloCuadroTambienEsUnVideo() throws Exception {
        Path unico = Path.of("target", "video-de-un-cuadro.mp4");
        Files.deleteIfExists(unico);

        // El caso que falla en el primer segundo: hay una sola pantalla que mostrar
        // y el video igual tiene que poder abrirse.
        CodificadorMp4.escribir(unico, List.of(new Cuadro(jpegDeColor(Color.BLUE), 0)), 4);

        assertSeParecaA(Color.BLUE,
                AWTFrameGrab.getFrame(unico.toFile(), 0).getRGB(ANCHO / 2, ALTO / 2),
                "el único cuadro");
        Files.deleteIfExists(unico);
    }
}
