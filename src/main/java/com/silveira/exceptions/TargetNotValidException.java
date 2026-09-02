package com.silveira.exceptions;

/** El target configurado no es LOCAL ni GRID, o falta la URL del Grid. */
public class TargetNotValidException extends FrameworkException {

    public TargetNotValidException(String mensaje) {
        super(mensaje);
    }

    public TargetNotValidException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
