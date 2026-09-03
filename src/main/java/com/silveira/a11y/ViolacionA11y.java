package com.silveira.a11y;

import java.util.List;

/**
 * Una regla de accesibilidad incumplida, con los elementos que la incumplen.
 *
 * axe-core devuelve mucho mas que esto por violacion. Lo que queda es lo que
 * sirve para decidir: que regla, que tan grave, donde, y donde leer como
 * arreglarlo. El resto es ruido en un reporte que alguien tiene que mirar.
 *
 * @param regla     identificador de axe, por ejemplo "color-contrast"
 * @param impacto   minor, moderate, serious o critical, segun axe
 * @param ayuda     que hay que hacer, en una linea
 * @param ayudaUrl  documentacion de Deque para esa regla
 * @param elementos selectores CSS de los nodos que la incumplen
 */
public record ViolacionA11y(String regla,
                            String impacto,
                            String ayuda,
                            String ayudaUrl,
                            List<String> elementos) {

    public int cantidad() {
        return elementos.size();
    }

    /** Orden de gravedad de axe, de mayor a menor. Lo desconocido va ultimo. */
    public int gravedad() {
        return switch (impacto == null ? "" : impacto) {
            case "critical" -> 0;
            case "serious"  -> 1;
            case "moderate" -> 2;
            case "minor"    -> 3;
            default         -> 4;
        };
    }

    @Override
    public String toString() {
        return regla + " (" + impacto + ", " + cantidad() + " elemento"
                + (cantidad() == 1 ? "" : "s") + "): " + ayuda;
    }
}
