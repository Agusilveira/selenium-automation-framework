package com.silveira.api;

import com.silveira.config.ConfigManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Token de autenticación del hilo actual.
 *
 * Por hilo y no compartido, por el mismo motivo que el WebDriver: con suites
 * paralelas, un token global es una condición de carrera esperando ocurrir. Un
 * test que hace logout o que se autentica como otro usuario le rompe la sesión a
 * los que corren al lado, y el fallo aparece en un caso que no tiene nada que ver.
 *
 * Se guarda para no volver a loguearse en cada request: el login es una llamada
 * de red más, y multiplicarla por cada caso agrega minutos a la suite.
 */
public final class AuthManager {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();
    private static final ThreadLocal<String> USUARIO = new ThreadLocal<>();

    private AuthManager() {
    }

    /** Token del usuario por defecto del perfil, logueándose solo la primera vez. */
    public static String token() {
        ConfigManager config = ConfigManager.get();
        return tokenDe(config.get("api.usuario"), config.get("api.password"));
    }

    public static String tokenDe(String usuario, String password) {
        if (TOKEN.get() != null && usuario.equals(USUARIO.get())) {
            return TOKEN.get();
        }

        LogUtils.info("Autenticando en la API como " + usuario);
        Response respuesta = given()
                .filter(new ApiLogFilter())
                .baseUri(ConfigManager.get().get("api.base.url"))
                .contentType(ContentType.JSON)
                .body(Map.of("username", usuario, "password", password))
                .post("/auth/login");

        if (respuesta.statusCode() != 200) {
            throw new FrameworkException(
                    "No se pudo autenticar en la API como '" + usuario + "'. "
                    + "Respondió " + respuesta.statusCode() + ": " + respuesta.asString());
        }

        String token = respuesta.jsonPath().getString("accessToken");
        if (token == null || token.isBlank()) {
            throw new FrameworkException(
                    "El login respondió 200 pero sin accessToken. Cuerpo: " + respuesta.asString());
        }

        TOKEN.set(token);
        USUARIO.set(usuario);
        return token;
    }

    public static boolean hayToken() {
        return TOKEN.get() != null;
    }

    /** Olvida el token del hilo. Necesario entre casos que usan usuarios distintos. */
    public static void limpiar() {
        TOKEN.remove();
        USUARIO.remove();
    }
}
