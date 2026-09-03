package com.silveira.api;

import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recorre un endpoint paginado y devuelve todo junto.
 *
 * Existe porque el bucle de paginación se escribe siempre igual y siempre se
 * escribe mal la primera vez: o corta de más, o entra en un ciclo infinito cuando
 * la API devuelve una página vacía en lugar de terminar.
 *
 * El tope de páginas no es opcional. Un endpoint que ignora el parámetro de
 * paginación devuelve siempre lo mismo, y sin tope el test se cuelga hasta el
 * timeout de la suite en vez de fallar diciendo qué pasó.
 */
public final class Paginador {

    private static final int TOPE_DE_PAGINAS = 50;

    private Paginador() {
    }

    /**
     * Trae todos los elementos recorriendo las páginas.
     *
     * @param ruta        endpoint, por ejemplo "/products"
     * @param campoLista  ruta JSON de la lista dentro de la respuesta, por ejemplo "products"
     * @param campoTotal  ruta JSON del total de elementos, por ejemplo "total"
     * @param porPagina   cuántos pedir por página
     */
    public static <T> List<T> todos(String ruta, String campoLista, String campoTotal,
                                    int porPagina, Class<T> tipo) {
        List<T> acumulado = new ArrayList<>();
        int saltar = 0;
        int total = -1;
        int pagina = 0;

        while (pagina++ < TOPE_DE_PAGINAS) {
            Map<String, Object> parametros = new HashMap<>();
            parametros.put("limit", porPagina);
            parametros.put("skip", saltar);

            ApiResponse respuesta = ApiClient.get(ruta, parametros).esExitosa();
            List<T> pagActual = respuesta.comoListaDe(campoLista, tipo);

            if (total < 0) {
                total = respuesta.campo(campoTotal);
                LogUtils.info("Paginando " + ruta + ": " + total + " elementos de a " + porPagina);
            }

            // Una página vacía es el fin, aunque el total diga otra cosa: confiar
            // solo en el total deja el bucle girando si la API miente.
            if (pagActual.isEmpty()) break;

            acumulado.addAll(pagActual);
            if (acumulado.size() >= total) break;
            saltar += porPagina;
        }

        if (pagina > TOPE_DE_PAGINAS) {
            throw new FrameworkException(
                    "Paginando " + ruta + " se superaron las " + TOPE_DE_PAGINAS + " páginas. "
                    + "Lo más probable es que el endpoint esté ignorando 'skip' y devuelva "
                    + "siempre lo mismo.");
        }

        return acumulado;
    }
}
