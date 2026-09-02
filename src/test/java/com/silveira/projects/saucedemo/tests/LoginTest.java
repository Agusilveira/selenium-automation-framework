package com.silveira.projects.saucedemo.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseTest;
import com.silveira.config.ConfigManager;
import com.silveira.dataprovider.DataProviderManager;
import com.silveira.projects.saucedemo.pages.InventoryPage;
import com.silveira.projects.saucedemo.pages.LoginPage;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginTest extends BaseTest {

    private final LoginPage login = new LoginPage();
    private final InventoryPage inventario = new InventoryPage();

    private String password() {
        return ConfigManager.get().get("sauce.password", "secret_sauce");
    }

    @Test(groups = {"smoke", "regresion"},
          description = "Un usuario válido llega al listado de productos")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"smoke", "login"})
    public void usuarioValidoAccedeAlInventario() {
        login.ingresarYEsperarInventario("standard_user", password());

        assertThat(inventario.estaCargada())
                .as("el listado de productos debería estar visible")
                .isTrue();
    }

    @Test(groups = "regresion",
          description = "Un usuario bloqueado ve el mensaje de error correspondiente")
    @FrameworkAnnotation(autor = "Agustín", categoria = "login")
    public void usuarioBloqueadoVeElError() {
        login.ingresar("locked_out_user", password());

        assertThat(login.mensajeDeError())
                .as("mensaje mostrado al usuario bloqueado")
                .contains("locked out");
    }

    @Test(groups = "regresion",
          dataProvider = "usuariosJson", dataProviderClass = DataProviderManager.class,
          description = "Distintos usuarios producen distinto resultado (datos en JSON)")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"login", "data-driven"})
    public void loginDataDrivenDesdeJson(Map<String, Object> caso) {
        login.ingresar(String.valueOf(caso.get("usuario")), String.valueOf(caso.get("password")));
        verificarResultado(String.valueOf(caso.get("resultado")));
    }

    @Test(groups = "regresion",
          dataProvider = "usuariosExcel", dataProviderClass = DataProviderManager.class,
          description = "Los mismos casos, con los datos en una planilla")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"login", "data-driven"})
    public void loginDataDrivenDesdeExcel(Map<String, String> caso) {
        login.ingresar(caso.get("usuario"), caso.get("password"));
        verificarResultado(caso.get("resultado"));
    }

    private void verificarResultado(String esperado) {
        switch (esperado) {
            case "exito" -> assertThat(inventario.estaCargada())
                    .as("esperaba entrar al inventario")
                    .isTrue();
            case "error" -> assertThat(login.hayError())
                    .as("esperaba un mensaje de error")
                    .isTrue();
            default -> throw new IllegalArgumentException(
                    "Resultado no reconocido: '" + esperado + "'. Usá exito o error.");
        }
    }
}
