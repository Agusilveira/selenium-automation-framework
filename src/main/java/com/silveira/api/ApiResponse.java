package com.silveira.api;

import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envoltorio de la respuesta con aserciones encadenables.
 *
 * Existe por una razón concreta: cuando falla una aserción sobre una respuesta,
 * el mensaje por defecto dice "expected 200 but was 404" y nada más. Acá cada
 * fallo incluye el método, la ruta y el cuerpo, que es lo que hace falta para
 * entender qué pasó sin volver a ejecutar nada.
 *
 * No reemplaza a RestAssured: response() devuelve la Response cruda para lo que
 * no esté cubierto.
 */
public final class ApiResponse {

    private final Response response;
    private final String ruta;

    private ApiResponse(Response response, String ruta) {
        this.response = response;
        this.ruta = ruta;
    }

    static ApiResponse de(Response response, String ruta) {
        return new ApiResponse(response, ruta);
    }

    // ------------------------------------------------------------------
    // Acceso
    // ------------------------------------------------------------------

    public Response response() {
        return response;
    }

    public int codigo() {
        return response.statusCode();
    }

    public String cuerpo() {
        return response.asString();
    }

    public long tiempoMs() {
        return response.timeIn(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public <T> T campo(String ruta) {
        return response.jsonPath().get(ruta);
    }

    public Map<String, Object> comoMapa() {
        return response.jsonPath().getMap("$");
    }

    public <T> T comoObjeto(Class<T> tipo) {
        try {
            return response.as(tipo);
        } catch (Exception e) {
            throw new FrameworkException(
                    "No se pudo convertir la respuesta de " + ruta + " a " + tipo.getSimpleName()
                    + ". Cuerpo: " + recorte(), e);
        }
    }

    public <T> List<T> comoListaDe(String rutaJson, Class<T> tipo) {
        return response.jsonPath().getList(rutaJson, tipo);
    }

    // ------------------------------------------------------------------
    // Aserciones
    // ------------------------------------------------------------------

    public ApiResponse tieneCodigo(int esperado) {
        assertThat(codigo())
                .as("código de %s%n%s", ruta, recorte())
                .isEqualTo(esperado);
        return this;
    }

    public ApiResponse esExitosa() {
        assertThat(codigo())
                .as("se esperaba un 2xx de %s%n%s", ruta, recorte())
                .isBetween(200, 299);
        return this;
    }

    public ApiResponse tieneCampo(String rutaJson) {
        Object valor = response.jsonPath().get(rutaJson);
        assertThat(valor)
                .as("el campo '%s' debería existir en la respuesta de %s%n%s", rutaJson, ruta, recorte())
                .isNotNull();
        return this;
    }

    public ApiResponse campoEs(String rutaJson, Object esperado) {
        Object valor = response.jsonPath().get(rutaJson);
        assertThat(valor)
                .as("campo '%s' de %s", rutaJson, ruta)
                .isEqualTo(esperado);
        return this;
    }

    public ApiResponse respondeEnMenosDe(long milisegundos) {
        assertThat(tiempoMs())
                .as("tiempo de respuesta de %s", ruta)
                .isLessThanOrEqualTo(milisegundos);
        return this;
    }

    /**
     * Valida la respuesta contra un JSON Schema de src/test/resources/schemas.
     *
     * Es lo que convierte un test de "devolvió 200" en uno que verifica la forma
     * del contrato: si mañana la API renombra un campo o le cambia el tipo, el
     * test falla aunque el código siga siendo 200.
     */
    public ApiResponse cumpleElEsquema(String archivo) {
        try {
            response.then().assertThat()
                    .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/" + archivo));
            LogUtils.info("La respuesta de " + ruta + " cumple el esquema " + archivo);
        } catch (AssertionError e) {
            throw new AssertionError(
                    "La respuesta de " + ruta + " no cumple el esquema " + archivo + ".\n"
                    + e.getMessage(), e);
        }
        return this;
    }

    private String recorte() {
        String cuerpo = cuerpo();
        return cuerpo.length() <= 600 ? cuerpo : cuerpo.substring(0, 600) + "...";
    }
}
