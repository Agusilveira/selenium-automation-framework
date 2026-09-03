package com.silveira.keywords;

import java.util.ArrayList;
import java.util.List;

/**
 * Acumula los fallos blandos del hilo actual.
 *
 * Una acción con CONTINUE_ON_FAILURE no corta el caso, pero el fallo tiene que
 * llegar a alguna parte: si no, el test termina en verde y la opción es una forma
 * elegante de esconder errores.
 *
 * Quien los revisa es SoftFailureListener, después de cada método de test. Acá
 * solo se juntan.
 *
 * Por hilo, porque con suites paralelas los fallos de un caso no tienen que
 * aparecer en el reporte de otro.
 */
public final class SoftFailures {

    private static final ThreadLocal<List<String>> ACUMULADOS =
            ThreadLocal.withInitial(ArrayList::new);

    private SoftFailures() {
    }

    public static void registrar(String detalle) {
        ACUMULADOS.get().add(detalle);
    }

    public static List<String> registrados() {
        return List.copyOf(ACUMULADOS.get());
    }

    public static boolean hay() {
        return !ACUMULADOS.get().isEmpty();
    }

    public static int cantidad() {
        return ACUMULADOS.get().size();
    }

    /** Limpia el hilo. Sin esto, el caso siguiente hereda los fallos del anterior. */
    public static void limpiar() {
        ACUMULADOS.get().clear();
        ACUMULADOS.remove();
    }

    /** Un solo mensaje con todos los fallos, numerados, para el reporte. */
    public static String resumen() {
        List<String> fallos = registrados();
        StringBuilder sb = new StringBuilder(
                fallos.size() + " fallo(s) durante el caso (CONTINUE_ON_FAILURE):");
        for (int i = 0; i < fallos.size(); i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(fallos.get(i));
        }
        return sb.toString();
    }
}
