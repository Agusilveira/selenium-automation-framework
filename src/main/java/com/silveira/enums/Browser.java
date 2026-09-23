package com.silveira.enums;

import com.silveira.exceptions.BrowserNotSupportedException;

import java.util.Arrays;

/**
 * Navegadores soportados por BrowserFactory.
 */
public enum Browser {
    CHROME,
    FIREFOX,
    EDGE;

    /**
     * Desde texto de configuración, sin importar mayúsculas ni espacios.
     *
     * Un solo lugar para convertir: el perfil y el XML de la suite fallan con el
     * mismo mensaje, que repite lo que se escribió y no la versión normalizada.
     */
    public static Browser de(String valor) {
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BrowserNotSupportedException("Navegador no soportado: '" + valor.trim()
                    + "'. Opciones: " + Arrays.toString(values()), e);
        }
    }
}
