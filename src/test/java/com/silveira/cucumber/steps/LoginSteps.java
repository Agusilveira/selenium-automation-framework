package com.silveira.cucumber.steps;

import com.silveira.config.ConfigManager;
import com.silveira.projects.saucedemo.pages.InventoryPage;
import com.silveira.projects.saucedemo.pages.LoginPage;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los steps no tienen logica: traducen Gherkin a llamadas sobre las MISMAS
 * paginas que usa la suite de TestNG. Si un locator cambia, se arregla en un
 * solo lugar y los dos caminos quedan al dia.
 */
public class LoginSteps {

    private final LoginPage login = new LoginPage();
    private final InventoryPage inventario = new InventoryPage();

    private String clavePorDefecto() {
        return ConfigManager.get().get("sauce.password", "secret_sauce");
    }

    @Dado("que estoy en la página de login")
    public void queEstoyEnLaPaginaDeLogin() {
        // El navegador ya quedo en la URL base desde el hook.
    }

    @Cuando("ingreso con {string}")
    public void ingresoCon(String usuario) {
        login.ingresar(usuario, clavePorDefecto());
    }

    @Cuando("ingreso con {string} y la clave {string}")
    public void ingresoConYLaClave(String usuario, String clave) {
        login.ingresar(usuario, clave);
    }

    @Entonces("veo la lista de productos")
    public void veoLaListaDeProductos() {
        assertThat(inventario.estaCargada())
                .as("el listado de productos deberia estar visible")
                .isTrue();
    }

    @Entonces("veo un mensaje de error que contiene {string}")
    public void veoUnMensajeDeErrorQueContiene(String fragmento) {
        assertThat(login.mensajeDeError())
                .as("mensaje de error mostrado")
                .contains(fragmento);
    }

    @Entonces("el resultado es {string}")
    public void elResultadoEs(String esperado) {
        switch (esperado) {
            case "exito" -> assertThat(inventario.estaCargada())
                    .as("esperaba entrar al inventario").isTrue();
            case "error" -> assertThat(login.hayError())
                    .as("esperaba un mensaje de error").isTrue();
            default -> throw new IllegalArgumentException("Resultado no reconocido: " + esperado);
        }
    }
}
