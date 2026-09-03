package com.silveira.api;

import com.silveira.config.ConfigManager;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Punto de entrada de la capa de API.
 *
 * Devuelve ApiResponse y no la Response cruda de RestAssured para que los tests
 * lean como afirmaciones y no como plomería. Todo lo común —URL base, timeouts,
 * content type, y el filtro que deja el intercambio en el reporte— se configura
 * acá una vez, no en cada llamada.
 *
 * Hay dos variantes de cada verbo: la anónima y la autenticada. Que la
 * autenticación sea explícita en el nombre del método evita el error de creer que
 * un endpoint estaba protegido cuando en realidad se estaba llamando sin token.
 */
public final class ApiClient {

    private ApiClient() {
    }

    // ------------------------------------------------------------------
    // Especificaciones
    // ------------------------------------------------------------------

    private static RequestSpecification base() {
        ConfigManager config = ConfigManager.get();
        int timeoutMs = config.getInt("api.timeout", 20) * 1000;

        return given().spec(new RequestSpecBuilder()
                .setBaseUri(config.get("api.base.url"))
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new ApiLogFilter())
                .addFilter(new RateLimitFilter())
                .setConfig(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", timeoutMs)
                                .setParam("http.socket.timeout", timeoutMs)))
                .build());
    }

    /** Spec con el token del usuario por defecto del perfil. */
    public static RequestSpecification autenticado() {
        return base().header("Authorization", "Bearer " + AuthManager.token());
    }

    public static RequestSpecification autenticadoComo(String usuario, String password) {
        return base().header("Authorization", "Bearer " + AuthManager.tokenDe(usuario, password));
    }

    public static RequestSpecification anonimo() {
        return base();
    }

    /**
     * Spec con un token ya obtenido.
     *
     * AuthManager sirve para APIs que autentican con usuario y contrasena contra un
     * endpoint de login. Muchas usan un token emitido por fuera, y forzarlas por
     * ese camino obligaria a inventar un login que no existe.
     */
    public static RequestSpecification conToken(String token) {
        return base().header("Authorization", "token " + token);
    }

    public static ApiResponse getConToken(String ruta, String token) {
        return ApiResponse.de(conToken(token).get(ruta), ruta);
    }

    public static ApiResponse getConToken(String ruta, String token, Map<String, ?> parametros) {
        return ApiResponse.de(conToken(token).queryParams(parametros).get(ruta), ruta);
    }

    public static ApiResponse postConToken(String ruta, Object cuerpo, String token) {
        return ApiResponse.de(conToken(token).body(cuerpo).post(ruta), ruta);
    }

    public static ApiResponse patchConToken(String ruta, Object cuerpo, String token) {
        return ApiResponse.de(conToken(token).body(cuerpo).patch(ruta), ruta);
    }

    public static ApiResponse deleteConToken(String ruta, String token) {
        return ApiResponse.de(conToken(token).delete(ruta), ruta);
    }

    // ------------------------------------------------------------------
    // Verbos sin autenticación
    // ------------------------------------------------------------------

    public static ApiResponse get(String ruta) {
        return ApiResponse.de(base().get(ruta), ruta);
    }

    public static ApiResponse get(String ruta, Map<String, ?> parametros) {
        return ApiResponse.de(base().queryParams(parametros).get(ruta), ruta);
    }

    public static ApiResponse post(String ruta, Object cuerpo) {
        return ApiResponse.de(base().body(cuerpo).post(ruta), ruta);
    }

    public static ApiResponse put(String ruta, Object cuerpo) {
        return ApiResponse.de(base().body(cuerpo).put(ruta), ruta);
    }

    public static ApiResponse patch(String ruta, Object cuerpo) {
        return ApiResponse.de(base().body(cuerpo).patch(ruta), ruta);
    }

    public static ApiResponse delete(String ruta) {
        return ApiResponse.de(base().delete(ruta), ruta);
    }

    // ------------------------------------------------------------------
    // Verbos autenticados
    // ------------------------------------------------------------------

    public static ApiResponse getAuth(String ruta) {
        return ApiResponse.de(autenticado().get(ruta), ruta);
    }

    public static ApiResponse getAuth(String ruta, Map<String, ?> parametros) {
        return ApiResponse.de(autenticado().queryParams(parametros).get(ruta), ruta);
    }

    public static ApiResponse postAuth(String ruta, Object cuerpo) {
        return ApiResponse.de(autenticado().body(cuerpo).post(ruta), ruta);
    }

    public static ApiResponse putAuth(String ruta, Object cuerpo) {
        return ApiResponse.de(autenticado().body(cuerpo).put(ruta), ruta);
    }

    public static ApiResponse deleteAuth(String ruta) {
        return ApiResponse.de(autenticado().delete(ruta), ruta);
    }
}
