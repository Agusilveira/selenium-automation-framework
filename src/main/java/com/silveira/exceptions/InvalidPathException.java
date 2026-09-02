package com.silveira.exceptions;

/** Una ruta de archivo o recurso no existe o no se puede leer. */
public class InvalidPathException extends FrameworkException {

    public InvalidPathException(String mensaje) {
        super(mensaje);
    }

    public InvalidPathException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
