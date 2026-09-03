package com.silveira.api;

import com.silveira.utils.LogUtils;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Reintenta cuando la API responde 429, respetando la espera que pida.
 *
 * Un 429 no es un fallo de la aplicación ni del test: es el servidor diciendo
 * "más despacio". Sin esto, cualquier suite que corra en paralelo contra una API
 * compartida produce fallos intermitentes que no son defectos, y el equipo
 * aprende a ignorar los rojos. Eso es peor que no tener tests.
 *
 * Si la respuesta trae `Retry-After` se respeta; si no, la espera se duplica en
 * cada intento. Y hay un tope: reintentar indefinidamente convierte un límite de
 * peticiones en una suite colgada.
 *
 * Lo que NO hace es esconderlo. Cada reintento queda en el log, y si se acaban los
 * intentos la respuesta 429 se devuelve tal cual para que el test falle diciendo
 * exactamente qué pasó.
 */
public class RateLimitFilter implements Filter {

    private static final int MAXIMO_REINTENTOS = 3;
    private static final long ESPERA_INICIAL_MS = 1000;

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification response,
                           FilterContext contexto) {

        Response respuesta = contexto.next(request, response);
        long espera = ESPERA_INICIAL_MS;

        for (int intento = 1; intento <= MAXIMO_REINTENTOS && respuesta.statusCode() == 429; intento++) {
            long pausa = esperaPedida(respuesta).orElse(espera);
            LogUtils.warn("La API respondió 429 en " + request.getMethod() + " " + request.getURI()
                    + ". Esperando " + pausa + " ms antes del intento " + (intento + 1)
                    + " de " + (MAXIMO_REINTENTOS + 1));

            try {
                Thread.sleep(pausa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return respuesta;
            }

            respuesta = contexto.next(request, response);
            espera *= 2;
        }

        if (respuesta.statusCode() == 429) {
            LogUtils.error("La API sigue respondiendo 429 después de " + MAXIMO_REINTENTOS
                    + " reintentos. Puede que la suite le esté pegando con demasiada "
                    + "concurrencia, o que el límite sea por tiempo y no por ráfaga.");
        }

        return respuesta;
    }

    /** Segundos que pide el servidor en Retry-After, si los pide. */
    private java.util.Optional<Long> esperaPedida(Response respuesta) {
        String cabecera = respuesta.getHeader("Retry-After");
        if (cabecera == null || cabecera.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Long.parseLong(cabecera.trim()) * 1000);
        } catch (NumberFormatException e) {
            // Retry-After tambien admite una fecha. No vale la pena parsearla: se
            // cae al respaldo exponencial, que para este caso alcanza.
            return java.util.Optional.empty();
        }
    }
}
