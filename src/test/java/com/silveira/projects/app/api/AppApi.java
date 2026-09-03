package com.silveira.projects.app.api;

import com.silveira.api.ApiClient;
import com.silveira.api.ApiResponse;
import com.silveira.config.ConfigManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.helpers.FileHelper;
import com.silveira.helpers.PropertiesHelper;

import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de la aplicación bajo prueba.
 *
 * Se apoya en el `ApiClient` del framework, así que hereda el logging del
 * intercambio HTTP al reporte y las aserciones de `ApiResponse` sin volver a
 * escribir nada de eso.
 *
 * El token no se versiona: lo genera `preparar-app.sh` y se lee de un archivo
 * ignorado por git. Una credencial dentro del repositorio es una credencial
 * filtrada, aunque la aplicación corra en localhost.
 */
public final class AppApi {

    private static final String ARCHIVO_TOKEN = "src/test/resources/config/.app-token";

    private AppApi() {
    }

    public static String token() {
        if (!FileHelper.existe(ARCHIVO_TOKEN)) {
            throw new FrameworkException("""
                    No hay token de API para la aplicacion bajo prueba.
                    Generalo con:
                      docker compose -f docker-compose.app.yml up -d
                      ./scripts/preparar-app.sh""");
        }
        return PropertiesHelper.get(ARCHIVO_TOKEN, "app.token");
    }

    private static String usuario() {
        return ConfigManager.get().get("app.usuario");
    }

    private static String repo() {
        return ConfigManager.get().get("app.repo");
    }

    private static String rutaIssues() {
        return "/repos/" + usuario() + "/" + repo() + "/issues";
    }

    // ------------------------------------------------------------------

    public static ApiResponse quienSoy() {
        return ApiClient.getConToken("/user", token());
    }

    public static ApiResponse crearIssue(String titulo, String cuerpo) {
        return ApiClient.postConToken(rutaIssues(),
                Map.of("title", titulo, "body", cuerpo), token());
    }

    public static ApiResponse obtenerIssue(int numero) {
        return ApiClient.getConToken(rutaIssues() + "/" + numero, token());
    }

    public static ApiResponse cerrarIssue(int numero) {
        return ApiClient.patchConToken(rutaIssues() + "/" + numero,
                Map.of("state", "closed"), token());
    }

    public static List<String> titulosDeIssues() {
        return ApiClient.getConToken(rutaIssues(), token(), Map.of("state", "all"))
                .esExitosa()
                .comoListaDe("title", String.class);
    }

    public static ApiResponse listarIssues() {
        return ApiClient.getConToken(rutaIssues(), token(), Map.of("state", "all"));
    }
}
