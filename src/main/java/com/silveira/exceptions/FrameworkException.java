package com.silveira.exceptions;

/**
 * Base de todas las excepciones del framework.
 *
 * Es unchecked a proposito: un fallo de configuracion o de infraestructura no es
 * algo que un test deba capturar y manejar, es algo que tiene que cortar la
 * ejecucion con un mensaje claro.
 */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String mensaje) {
        super(mensaje);
    }

    public FrameworkException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
