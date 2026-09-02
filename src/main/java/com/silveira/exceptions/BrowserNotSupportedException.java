package com.silveira.exceptions;

/** Se pidio un navegador que BrowserFactory no sabe construir. */
public class BrowserNotSupportedException extends FrameworkException {

    public BrowserNotSupportedException(String mensaje) {
        super(mensaje);
    }

    public BrowserNotSupportedException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
