package com.silveira.projects.dummyjson.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.api.ApiClient;
import com.silveira.api.AuthManager;
import com.silveira.common.BaseApiTest;
import com.silveira.config.ConfigManager;
import com.silveira.projects.dummyjson.models.Usuario;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthApiTest extends BaseApiTest {

    private String usuario() { return ConfigManager.get().get("api.usuario"); }
    private String password() { return ConfigManager.get().get("api.password"); }

    @Test(groups = {"api", "smoke"}, description = "Un login valido devuelve un token usable")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "auth"})
    public void unLoginValidoDevuelveUnTokenUsable() {
        String token = AuthManager.tokenDe(usuario(), password());

        assertThat(token).as("token devuelto por el login").isNotBlank();
        // Un JWT tiene tres partes separadas por punto. Verificar la forma evita
        // dar por bueno un token que en realidad es un mensaje de error.
        assertThat(token.chars().filter(c -> c == 46).count())
                .as("un JWT tiene tres partes separadas por punto")
                .isEqualTo(2);
    }

    @Test(groups = "api", description = "El endpoint protegido responde con el usuario autenticado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "auth"})
    public void elEndpointProtegidoDevuelveAlUsuarioAutenticado() {
        Usuario yo = ApiClient.getAuth("/auth/me").tieneCodigo(200).comoObjeto(Usuario.class);

        assertThat(yo.username()).as("usuario autenticado").isEqualTo(usuario());
        assertThat(yo.email()).as("email").contains("@");
    }

    @Test(groups = "api", description = "El mismo endpoint sin token rechaza el acceso")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "auth", "negativos"})
    public void elEndpointProtegidoSinTokenRechaza() {
        // Es la contracara del caso anterior y la que de verdad prueba que el
        // endpoint este protegido: sin este, un endpoint abierto pasaria igual.
        ApiClient.get("/auth/me").tieneCodigo(401);
    }

    @Test(groups = "api", description = "Credenciales invalidas no devuelven token")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "auth", "negativos"})
    public void credencialesInvalidasNoDevuelvenToken() {
        assertThatThrownBy(() -> AuthManager.tokenDe(usuario(), "clave-que-no-es"))
                .as("el login con clave incorrecta deberia fallar de forma explicita")
                .hasMessageContaining("No se pudo autenticar");
    }

    @Test(groups = "api", description = "El token se reutiliza en vez de re-loguearse")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "auth"})
    public void elTokenSeReutiliza() {
        String primero = AuthManager.tokenDe(usuario(), password());
        String segundo = AuthManager.tokenDe(usuario(), password());

        assertThat(segundo)
                .as("el segundo pedido deberia devolver el token cacheado, no uno nuevo")
                .isSameAs(primero);
    }

    @Test(groups = "api", description = "Un usuario nuevo se crea con los datos enviados")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "usuarios"})
    public void creaUnUsuario() {
        ApiClient.post("/users/add", Map.of(
                        "firstName", "Agustin",
                        "lastName", "Silveira",
                        "age", 30))
                .tieneCodigo(201)
                .campoEs("firstName", "Agustin")
                .tieneCampo("id");
    }
}
