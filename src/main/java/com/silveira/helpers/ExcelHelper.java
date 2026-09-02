package com.silveira.helpers;

import com.silveira.exceptions.FrameworkException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura y escritura de Excel para datos de test.
 *
 * La primera fila son los encabezados y cada fila siguiente es un caso. Los
 * valores se leen siempre como texto con DataFormatter: un código postal "05000"
 * o un teléfono no deben convertirse en número y perder el cero o ganar notación
 * científica.
 */
public final class ExcelHelper {

    private static final DataFormatter FORMATEADOR = new DataFormatter();

    private ExcelHelper() {
    }

    public static List<Map<String, String>> leerHoja(String rutaRelativa, String hoja) {
        Path path = FileHelper.exigir(rutaRelativa);

        try (InputStream in = Files.newInputStream(path);
             Workbook libro = new XSSFWorkbook(in)) {

            Sheet pestania = libro.getSheet(hoja);
            if (pestania == null) {
                throw new FrameworkException(
                        "El archivo " + rutaRelativa + " no tiene la hoja '" + hoja + "'");
            }

            Row encabezados = pestania.getRow(pestania.getFirstRowNum());
            if (encabezados == null) {
                return List.of();
            }

            List<String> nombres = new ArrayList<>();
            for (Cell celda : encabezados) {
                nombres.add(FORMATEADOR.formatCellValue(celda).trim());
            }

            List<Map<String, String>> filas = new ArrayList<>();
            for (int i = pestania.getFirstRowNum() + 1; i <= pestania.getLastRowNum(); i++) {
                Row fila = pestania.getRow(i);
                if (fila == null) continue;

                Map<String, String> valores = new LinkedHashMap<>();
                boolean vacia = true;
                for (int c = 0; c < nombres.size(); c++) {
                    String valor = FORMATEADOR.formatCellValue(fila.getCell(c)).trim();
                    valores.put(nombres.get(c), valor);
                    if (!valor.isEmpty()) vacia = false;
                }
                // Excel deja filas fantasma al borrar contenido: se descartan.
                if (!vacia) filas.add(valores);
            }
            return filas;

        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer el Excel " + rutaRelativa, e);
        }
    }

    /** El formato que espera un @DataProvider de TestNG: cada fila, un caso. */
    public static Object[][] comoDataProvider(String rutaRelativa, String hoja) {
        List<Map<String, String>> filas = leerHoja(rutaRelativa, hoja);
        Object[][] datos = new Object[filas.size()][1];
        for (int i = 0; i < filas.size(); i++) {
            datos[i][0] = filas.get(i);
        }
        return datos;
    }

    /** Crea un Excel a partir de filas mapeadas. Usado por los tests del helper. */
    public static void escribirHoja(String rutaRelativa, String hoja,
                                    List<Map<String, String>> filas) {
        Path path = FileHelper.ruta(rutaRelativa);
        try (Workbook libro = new XSSFWorkbook()) {
            Sheet pestania = libro.createSheet(hoja);

            if (!filas.isEmpty()) {
                List<String> nombres = new ArrayList<>(filas.get(0).keySet());
                Row encabezados = pestania.createRow(0);
                for (int c = 0; c < nombres.size(); c++) {
                    encabezados.createCell(c).setCellValue(nombres.get(c));
                }
                for (int f = 0; f < filas.size(); f++) {
                    Row fila = pestania.createRow(f + 1);
                    for (int c = 0; c < nombres.size(); c++) {
                        fila.createCell(c).setCellValue(filas.get(f).get(nombres.get(c)));
                    }
                }
            }

            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                libro.write(out);
            }
        } catch (IOException e) {
            throw new FrameworkException("No se pudo escribir el Excel " + path, e);
        }
    }
}
