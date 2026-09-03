package com.silveira.api;

import com.silveira.reports.AllureManager;
import com.silveira.reports.ExtentTestManager;
import com.silveira.utils.LogUtils;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.List;
import java.util.Locale;

/**
 * Deja cada intercambio HTTP en el log y en el reporte, sin que el test pida nada.
 *
 * Es la razón de ser de esta capa. RestAssured ya sabe hacer requests, igual que
 * Selenium ya sabe clickear: envolverlo por envolverlo no aporta. Lo que sí aporta
 * es que cuando un test de API falla, quien abre el reporte vea qué se mandó y qué
 * volvió sin tener que reproducirlo a mano.
 *
 * Las cabeceras sensibles se enmascaran. Un reporte de CI es público dentro del
 * equipo y a veces fuera, y un token pegado ahí es una credencial filtrada.
 */
public class ApiLogFilter implements Filter {

    private static final List<String> SENSIBLES =
            List.of("authorization", "cookie", "set-cookie", "x-api-key", "proxy-authorization");

    /** Un cuerpo enorme en el reporte lo vuelve inusable; con el principio alcanza. */
    private static final int MAXIMO_CUERPO = 4000;

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification response,
                           FilterContext contexto) {

        long comienzo = System.currentTimeMillis();
        Response respuesta = contexto.next(request, response);
        long duracion = System.currentTimeMillis() - comienzo;

        String resumen = request.getMethod() + " " + request.getURI()
                + " -> " + respuesta.getStatusCode() + " en " + duracion + " ms";
        LogUtils.info(resumen);

        String detalle = armarDetalle(request, respuesta, duracion);
        ExtentTestManager.info("<pre>" + escaparHtml(detalle) + "</pre>");
        AllureManager.adjuntarTexto(resumen, detalle);

        return respuesta;
    }

    private String armarDetalle(FilterableRequestSpecification request,
                                Response respuesta, long duracion) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- REQUEST ---\n");
        sb.append(request.getMethod()).append(' ').append(request.getURI()).append('\n');

        request.getHeaders().forEach(h ->
                sb.append(h.getName()).append(": ").append(enmascarar(h.getName(), h.getValue())).append('\n'));

        if (request.getBody() != null) {
            sb.append('\n').append(recortar(request.getBody().toString())).append('\n');
        }

        sb.append("\n--- RESPONSE (").append(respuesta.getStatusCode())
          .append(", ").append(duracion).append(" ms) ---\n");
        respuesta.getHeaders().forEach(h ->
                sb.append(h.getName()).append(": ").append(enmascarar(h.getName(), h.getValue())).append('\n'));
        sb.append('\n').append(recortar(respuesta.getBody().asString()));

        return sb.toString();
    }

    private String enmascarar(String nombre, String valor) {
        if (!SENSIBLES.contains(nombre.toLowerCase(Locale.ROOT))) return valor;
        if (valor == null || valor.length() <= 12) return "***";
        // Se dejan los primeros caracteres para poder distinguir un token de otro
        // en un log, sin que el valor sirva para nada.
        return valor.substring(0, 12) + "*** (" + valor.length() + " caracteres)";
    }

    private String recortar(String cuerpo) {
        if (cuerpo == null) return "";
        if (cuerpo.length() <= MAXIMO_CUERPO) return cuerpo;
        return cuerpo.substring(0, MAXIMO_CUERPO)
                + "\n... recortado, " + cuerpo.length() + " caracteres en total";
    }

    private String escaparHtml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
