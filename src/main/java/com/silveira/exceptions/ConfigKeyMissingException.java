package com.silveira.exceptions;

/** Falta una clave de configuracion obligatoria. */
public class ConfigKeyMissingException extends FrameworkException {

    public ConfigKeyMissingException(String mensaje) {
        super(mensaje);
    }

    public ConfigKeyMissingException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
