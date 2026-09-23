package com.silveira.config;

import com.silveira.enums.Browser;
import com.silveira.enums.Target;
import com.silveira.exceptions.ConfigKeyMissingException;
import com.silveira.exceptions.FrameworkException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;

/**
 * Resuelve la configuración con precedencia explícita y uniforme:
 * variable de entorno > propiedad de sistema > archivo del perfil > error.
 *
 * La clave "page.load.timeout" se busca fuera del archivo como PAGE_LOAD_TIMEOUT.
 * Todas las claves admiten override, sin excepciones arbitrarias: una clave que
 * solo se puede cambiar editando un archivo es una clave que no se puede cambiar
 * desde el CI.
 *
 * Una clave faltante o mal tipada falla acá, con un mensaje que la nombra, en vez
 * de propagarse como null y explotar tres capas más abajo.
 */
public final class ConfigManager {

    private static ConfigManager instancia;

    private final Properties propiedades;
    private final Function<String, String> overrides;

    /** Visible para tests: permite inyectar propiedades y overrides. */
    ConfigManager(Properties propiedades, Function<String, String> overrides) {
        this.propiedades = propiedades;
        this.overrides = overrides;
    }

    /** Instancia compartida. El archivo se lee una sola vez por ejecución. */
    public static synchronized ConfigManager get() {
        if (instancia == null) {
            instancia = new ConfigManager(leerPerfil(perfilActivo()), ConfigManager::desdeElSistema);
        }
        return instancia;
    }

    /** Nombre del perfil en uso. Sirve para decir en un reporte contra que se corrio. */
    public static String perfilActivo() {
        String perfil = System.getProperty("env");
        if (esVacio(perfil)) perfil = System.getenv("TEST_ENV");
        return esVacio(perfil) ? "local" : perfil;
    }

    private static String desdeElSistema(String clave) {
        String valor = System.getProperty(clave);
        return valor != null ? valor : System.getenv(clave);
    }

    private static Properties leerPerfil(String perfil) {
        String recurso = "/config/" + perfil + ".properties";
        try (InputStream in = ConfigManager.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new FrameworkException("No existe el perfil de configuración: " + recurso);
            }
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer el perfil " + recurso, e);
        }
    }

    private static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private static String aFormatoDeEntorno(String clave) {
        return clave.toUpperCase().replace('.', '_');
    }

    // --- Acceso genérico ---

    public String get(String clave) {
        String override = overrides.apply(aFormatoDeEntorno(clave));
        if (!esVacio(override)) return override;

        String valor = propiedades.getProperty(clave);
        if (esVacio(valor)) {
            throw new ConfigKeyMissingException(
                    "Falta la clave de configuración '" + clave + "'. "
                    + "Definila en el perfil o exportá " + aFormatoDeEntorno(clave) + ".");
        }
        return valor;
    }

    public String get(String clave, String porDefecto) {
        try {
            return get(clave);
        } catch (ConfigKeyMissingException e) {
            return porDefecto;
        }
    }

    public int getInt(String clave) {
        String crudo = get(clave);
        try {
            return Integer.parseInt(crudo.trim());
        } catch (NumberFormatException e) {
            throw new FrameworkException(
                    "La clave '" + clave + "' debe ser un entero, pero vale '" + crudo + "'.", e);
        }
    }

    public int getInt(String clave, int porDefecto) {
        try {
            return getInt(clave);
        } catch (ConfigKeyMissingException e) {
            return porDefecto;
        }
    }

    public boolean getBool(String clave) {
        return Boolean.parseBoolean(get(clave).trim());
    }

    // --- Accesos con nombre para las claves frecuentes ---

    public String baseUrl()      { return get("base.url"); }
    public boolean headless()    { return getBool("headless"); }
    public String gridUrl()      { return get("grid.url"); }

    /**
     * El navegador configurado.
     *
     * Si el valor vino de un override, el error lo dice. BROWSER es una variable
     * que muchos escritorios Linux ya definen para abrir links —xdg-open, firefox—
     * y sin esa pista el mensaje apunta al perfil, que está bien.
     */
    public Browser browser() {
        String valor = get("browser");
        try {
            return Browser.de(valor);
        } catch (FrameworkException e) {
            if (esVacio(overrides.apply("BROWSER"))) throw e;
            throw new FrameworkException(e.getMessage() + " El valor viene de BROWSER (propiedad "
                    + "de sistema o variable de entorno), no del perfil. Algunos escritorios "
                    + "Linux la definen para su navegador por defecto: pisala con -DBROWSER=chrome.", e);
        }
    }

    public Target target() {
        return Target.de(get("target", Target.LOCAL.name()));
    }

    public int explicitTimeout() {
        return getInt("explicit.timeout", FrameworkConstants.TIMEOUT_EXPLICITO_DEFAULT);
    }

    public int pageLoadTimeout() {
        return getInt("page.load.timeout", FrameworkConstants.TIMEOUT_PAGE_LOAD_DEFAULT);
    }

    public int reintentos() {
        return getInt("retry.count", 0);
    }

    /**
     * Si el framework puede recurrir a JavaScript cuando un click o un sendKeys no
     * produce efecto.
     *
     * Prendido en todos los perfiles: el evento perdido se reprodujo en local y en
     * CI, con y sin headless, así que no es del entorno. Lo que evita que tape
     * bugs propios no es apagarlo, es que cada uso se cuenta en FallbackTracker y
     * pasar el umbral de webui.fallback.js.max rompe la suite.
     *
     * El valor por defecto sigue siendo false: un perfil que no lo declara no
     * hereda un recurso que nadie decidió usar.
     */
    public boolean fallbackJsHabilitado() {
        return Boolean.parseBoolean(get("webui.fallback.js.enabled", "false").trim());
    }

    /** Cuantos usos del fallback se toleran antes de fallar la suite. */
    public int fallbackJsMaximo() {
        return getInt("webui.fallback.js.max", 0);
    }
}
