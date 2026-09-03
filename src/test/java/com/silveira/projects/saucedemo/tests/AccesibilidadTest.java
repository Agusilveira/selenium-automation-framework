package com.silveira.projects.saucedemo.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseTest;
import com.silveira.config.ConfigManager;
import com.silveira.enums.FailureHandling;
import com.silveira.keywords.WebUI;
import com.silveira.projects.saucedemo.pages.InventoryPage;
import com.silveira.projects.saucedemo.pages.LoginPage;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accesibilidad sobre pantallas que ya se prueban por otras razones.
 *
 * Estos casos existen para dejar el mecanismo demostrado de punta a punta. En un
 * proyecto real la forma de usarlo es la del último caso de acá: una línea más
 * dentro de un flujo que ya existía, así la pantalla se revisa con sus datos y su
 * estado reales en vez de vacía.
 */
public class AccesibilidadTest extends BaseTest {

    private final LoginPage login = new LoginPage();
    private final InventoryPage inventario = new InventoryPage();

    private String password() {
        return ConfigManager.get().get("sauce.password", "secret_sauce");
    }

    @Test(groups = {"a11y", "regresion"},
          description = "La accesibilidad del login no empeoró respecto de la línea base")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"a11y", "login"})
    public void elLoginNoEmpeoroSuAccesibilidad() {
        WebUI.verificarAccesibilidad("saucedemo-login");
    }

    @Test(groups = {"a11y", "regresion"},
          description = "La accesibilidad del listado de productos no empeoró")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"a11y", "inventario"})
    public void elInventarioNoEmpeoroSuAccesibilidad() {
        login.ingresarYEsperarInventario("standard_user", password());

        WebUI.verificarAccesibilidad("saucedemo-inventario");
    }

    /**
     * Así se usa de verdad: el caso prueba lo suyo y de paso mira accesibilidad.
     *
     * Con CONTINUE_ON_FAILURE una regresión de accesibilidad no corta el flujo
     * funcional: el caso termina en rojo igual, pero recién al final y habiendo
     * revisado también lo que venía después.
     */
    @Test(groups = {"a11y", "regresion"},
          description = "Un flujo funcional que además revisa accesibilidad")
    @FrameworkAnnotation(autor = "Agustín", categoria = {"a11y", "carrito"})
    public void elCarritoFuncionaYEsAccesible() {
        login.ingresarYEsperarInventario("standard_user", password());
        inventario.agregarAlCarrito("Sauce Labs Backpack");

        assertThat(inventario.cantidadEnCarrito())
                .as("el producto debería quedar en el carrito")
                .isEqualTo(1);

        WebUI.verificarAccesibilidad("saucedemo-inventario", FailureHandling.CONTINUE_ON_FAILURE);
    }
}
