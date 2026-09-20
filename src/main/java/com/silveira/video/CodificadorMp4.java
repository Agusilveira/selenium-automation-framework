package com.silveira.video;

import com.silveira.exceptions.FrameworkException;
import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Convierte los cuadros capturados en un MP4 reproducible.
 *
 * Separado de la captura a propósito: acá no hay navegador ni protocolo, solo
 * imágenes y tiempos. Eso permite probarlo con cuadros inventados, que es la
 * única forma de saber que el archivo que sale se puede abrir sin tener que
 * correr una suite entera para generar uno.
 *
 * <b>El tiempo se reconstruye, no se asume.</b> El navegador manda un cuadro
 * cuando la página cambia, no a intervalos regulares: veinte segundos de espera
 * no producen ningún cuadro. Encadenarlos tal cual daría un video donde las
 * esperas no existen, y las esperas son justamente lo que uno quiere ver cuando
 * investiga un fallo de timing. Por eso cada cuadro se repite tantas veces como
 * haga falta para ocupar el tiempo real que estuvo en pantalla.
 *
 * jcodec y no ffmpeg porque ffmpeg hay que tenerlo instalado, y eso es
 * exactamente lo que no se puede dar por sentado en un runner ni en la máquina de
 * quien clone el repo.
 */
public final class CodificadorMp4 {

    /** Una imagen y el momento de la corrida en que se vio. */
    public record Cuadro(byte[] jpeg, long milisegundos) {
    }

    private CodificadorMp4() {
    }

    public static Path escribir(Path destino, List<Cuadro> cuadros, int fps) {
        if (cuadros.isEmpty()) {
            throw new FrameworkException("No hay cuadros para escribir en " + destino);
        }

        List<BufferedImage> imagenes = decodificar(cuadros);
        BufferedImage primera = imagenes.get(0);
        // H.264 trabaja con bloques: un ancho o alto impar no se puede codificar.
        int ancho = primera.getWidth() & ~1;
        int alto = primera.getHeight() & ~1;

        try {
            Files.createDirectories(destino.getParent());
            AWTSequenceEncoder encoder =
                    AWTSequenceEncoder.createSequenceEncoder(destino.toFile(), fps);
            try {
                for (int i = 0; i < cantidadDeCuadrosDeSalida(cuadros, fps); i++) {
                    long instante = i * 1000L / fps;
                    encoder.encodeImage(normalizar(imagenes.get(indiceEn(cuadros, instante)), ancho, alto));
                }
            } finally {
                encoder.finish();
            }
            return destino;
        } catch (IOException e) {
            throw new FrameworkException("No se pudo escribir el video " + destino, e);
        }
    }

    /**
     * Cuantos cuadros tiene el video para durar lo que duro el caso.
     *
     * El "+ 1" no es un redondeo: sin el, el ultimo cuadro capturado nunca llega a
     * verse, porque el tiempo de salida termina justo antes de su instante. Y el
     * ultimo cuadro es la pantalla en el momento del fallo, que es exactamente la
     * que uno abre el video para mirar.
     */
    private static int cantidadDeCuadrosDeSalida(List<Cuadro> cuadros, int fps) {
        long duracion = cuadros.get(cuadros.size() - 1).milisegundos();
        return (int) (duracion * fps / 1000) + 1;
    }

    /** El último cuadro que ya se había mostrado en ese instante. */
    private static int indiceEn(List<Cuadro> cuadros, long instante) {
        int indice = 0;
        while (indice + 1 < cuadros.size() && cuadros.get(indice + 1).milisegundos() <= instante) {
            indice++;
        }
        return indice;
    }

    private static List<BufferedImage> decodificar(List<Cuadro> cuadros) {
        List<BufferedImage> imagenes = new ArrayList<>(cuadros.size());
        for (Cuadro cuadro : cuadros) {
            try {
                BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(cuadro.jpeg()));
                if (imagen == null) {
                    throw new FrameworkException("Un cuadro del video no es una imagen válida");
                }
                imagenes.add(imagen);
            } catch (IOException e) {
                throw new FrameworkException("No se pudo decodificar un cuadro del video", e);
            }
        }
        return imagenes;
    }

    /**
     * Todos los cuadros al mismo tamaño y al formato que espera el codificador.
     *
     * Si la ventana cambia de tamaño durante el caso, los cuadros dejan de medir
     * lo mismo y la codificación falla a mitad de camino. Escalar contra el primero
     * lo vuelve imposible.
     */
    private static BufferedImage normalizar(BufferedImage imagen, int ancho, int alto) {
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = salida.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(imagen, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return salida;
    }
}
