package com.silveira.keywords;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Lleva la cuenta de cuántas veces el framework tuvo que recurrir a JavaScript
 * porque el navegador no entregó un evento de entrada.
 *
 * Existe porque un aviso en el log se ignora. Al contarlos, mostrarlos en el
 * reporte y fallar la suite si se pasan de un umbral, el fallback deja de ser una
 * muleta invisible y pasa a ser algo acotado y medido: si el número crece, hay que
 * ir al problema de entrega, no subir el umbral.
 *
 * Compartido entre hilos, no por hilo: la pregunta que interesa es cuántas veces
 * pasó en toda la corrida.
 */
public final class FallbackTracker {

    private static final List<String> USOS = Collections.synchronizedList(new ArrayList<>());

    private FallbackTracker() {
    }

    public static void registrar(String detalle) {
        USOS.add(detalle);
    }

    public static int cantidad() {
        return USOS.size();
    }

    public static List<String> usos() {
        synchronized (USOS) {
            return List.copyOf(USOS);
        }
    }

    public static void limpiar() {
        USOS.clear();
    }

    public static String resumen() {
        int total = cantidad();
        if (total == 0) {
            return "El navegador entregó todos los eventos de entrada: no hizo falta JavaScript.";
        }
        StringBuilder sb = new StringBuilder(
                "Se recurrió a JavaScript " + total + " vez/veces porque el evento nativo no llegó:");
        for (String uso : usos()) {
            sb.append("\n  - ").append(uso);
        }
        return sb.toString();
    }
}
