package com.silveira.enums;

import com.silveira.exceptions.TargetNotValidException;

/**
 * Donde se ejecuta: local o contra un Selenium Grid remoto.
 */
public enum Target {
    LOCAL,
    GRID;

    /** Desde texto de configuración, sin importar mayúsculas ni espacios. */
    public static Target de(String valor) {
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TargetNotValidException(
                    "Target no válido: '" + valor.trim() + "'. Usá LOCAL o GRID.", e);
        }
    }
}
