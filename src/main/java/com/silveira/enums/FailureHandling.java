package com.silveira.enums;

/**
 * Que hace una accion de WebUI cuando falla.
 *
 * STOP_ON_FAILURE lanza y corta el caso. CONTINUE_ON_FAILURE registra el error
 * y sigue, util para validaciones acumulativas. OPTIONAL ni siquiera registra
 * error: la accion podia no aplicar.
 */
public enum FailureHandling {
    STOP_ON_FAILURE,
    CONTINUE_ON_FAILURE,
    OPTIONAL
}
