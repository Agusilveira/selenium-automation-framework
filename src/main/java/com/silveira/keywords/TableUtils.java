package com.silveira.keywords;

import com.silveira.driver.DriverManager;
import com.silveira.exceptions.FrameworkException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura de tablas HTML.
 *
 * Recibe el selector CSS de la tabla y deriva el resto. Trabajar por nombre de
 * encabezado en vez de por índice de columna hace que agregar una columna no
 * rompa los tests que leen las otras.
 */
public final class TableUtils {

    private TableUtils() {
    }

    public static List<String> encabezados(String tabla) {
        return WaitUtils.todosVisibles(By.cssSelector(tabla + " thead th")).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    /** Espera a que la tabla renderice: leer sin esperar devuelve 0 filas. */
    public static int cantidadDeFilas(String tabla) {
        return WaitUtils.todosVisibles(By.cssSelector(tabla + " tbody tr")).size();
    }

    /** Fila y columna empiezan en 1, como en la interfaz. */
    public static String celda(String tabla, int fila, int columna) {
        String selector = tabla + " tbody tr:nth-child(" + fila + ") td:nth-child(" + columna + ")";
        return WaitUtils.visible(By.cssSelector(selector)).getText().trim();
    }

    public static int indiceDeColumna(String tabla, String encabezado) {
        List<String> nombres = encabezados(tabla);
        for (int i = 0; i < nombres.size(); i++) {
            if (nombres.get(i).equalsIgnoreCase(encabezado.trim())) {
                return i + 1;
            }
        }
        throw new FrameworkException(
                "La tabla no tiene la columna '" + encabezado + "'. Tiene: " + nombres);
    }

    public static List<String> columna(String tabla, String encabezado) {
        int indice = indiceDeColumna(tabla, encabezado);
        String selector = tabla + " tbody tr td:nth-child(" + indice + ")";
        return DriverManager.get().findElements(By.cssSelector(selector)).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    /** Toda la tabla como lista de filas, cada una mapeada por nombre de columna. */
    public static List<Map<String, String>> comoMapa(String tabla) {
        List<String> nombres = encabezados(tabla);
        List<WebElement> filas =
                WaitUtils.todosVisibles(By.cssSelector(tabla + " tbody tr"));

        List<Map<String, String>> resultado = new ArrayList<>();
        for (WebElement fila : filas) {
            List<WebElement> celdas = fila.findElements(By.tagName("td"));
            Map<String, String> mapa = new LinkedHashMap<>();
            for (int i = 0; i < nombres.size() && i < celdas.size(); i++) {
                mapa.put(nombres.get(i), celdas.get(i).getText().trim());
            }
            resultado.add(mapa);
        }
        return resultado;
    }

    /** Número de la primera fila cuya columna indicada contiene el texto. */
    public static int buscarFila(String tabla, String encabezado, String texto) {
        List<String> valores = columna(tabla, encabezado);
        for (int i = 0; i < valores.size(); i++) {
            if (valores.get(i).contains(texto)) {
                return i + 1;
            }
        }
        throw new FrameworkException(
                "Ninguna fila tiene '" + texto + "' en la columna '" + encabezado + "'");
    }
}
