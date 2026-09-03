package com.silveira.exceptions;

/**
 * La pagina tiene violaciones de accesibilidad que la linea base no contemplaba.
 *
 * Es RuntimeException y no AssertionError para que `WebUI.intentar` pueda
 * aplicarle una politica de fallos como a cualquier otra accion: hay casos donde
 * conviene registrar la regresion de accesibilidad y seguir revisando el resto de
 * la pantalla en vez de cortar en la primera.
 */
public class AccesibilidadException extends FrameworkException {

    public AccesibilidadException(String mensaje) {
        super(mensaje);
    }
}
