package com.silveira.config;

/**
 * Rutas y valores fijos del framework. Todo lo que sea configurable por ambiente
 * vive en ConfigManager; acá solo lo que no cambia entre ejecuciones.
 */
public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    private static final String RECURSOS_TEST = "src/test/resources/";

    public static final String RUTA_CONFIG   = RECURSOS_TEST + "config/";
    public static final String RUTA_SUITES   = RECURSOS_TEST + "suites/";
    public static final String RUTA_OBJECTS  = RECURSOS_TEST + "objects/";
    public static final String RUTA_DATA     = RECURSOS_TEST + "data/";

    public static final String RUTA_REPORTES  = "reports/";
    public static final String RUTA_EVIDENCIA = "evidence/";
    public static final String REPORTE_EXTENT = RUTA_REPORTES + "ExtentReport.html";

    /** Usados solo si el perfil de configuración no define los suyos. */
    public static final int TIMEOUT_EXPLICITO_DEFAULT = 15;
    public static final int TIMEOUT_PAGE_LOAD_DEFAULT = 30;

    /** Espera corta para confirmar el efecto de una acción antes de reintentarla. */
    public static final int TIMEOUT_EFECTO_ACCION = 4;

    /** Intentos de una acción antes de recurrir a JavaScript. */
    public static final int INTENTOS_ACCION = 3;
}
