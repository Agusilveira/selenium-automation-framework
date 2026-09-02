package com.silveira.projects.saucedemo.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseTest;
import com.silveira.config.ConfigManager;
import com.silveira.projects.saucedemo.pages.CheckoutPage;
import com.silveira.projects.saucedemo.pages.InventoryPage;
import com.silveira.projects.saucedemo.pages.LoginPage;
import com.silveira.utils.FakeDataUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckoutTest extends BaseTest {

    private final LoginPage login = new LoginPage();
    private final InventoryPage inventario = new InventoryPage();
    private final CheckoutPage checkout = new CheckoutPage();

    /**
     * TestNG corre los @BeforeMethod de la superclase antes que los de la subclase,
     * asi que cuando esto se ejecuta el navegador de BaseTest ya esta abierto.
     *
     * Sin dependsOnMethods a proposito: entre metodos de configuracion de distintas
     * clases no resuelve, y falla en silencio. El metodo no corre, el test arranca
     * sin loguearse, y el error aparece recien al buscar un elemento del inventario.
     */
    @BeforeMethod(alwaysRun = true)
    public void loguearse() {
        login.ingresarYEsperarInventario(
                "standard_user",
                ConfigManager.get().get("sauce.password", "secret_sauce"));
    }

    @Test(groups = {"smoke", "regresion"},
          description = "Agregar y quitar productos actualiza el contador del carrito")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"smoke", "carrito"})
    public void elContadorDelCarritoRefleja() {
        inventario.agregarAlCarrito("Sauce Labs Backpack");
        inventario.agregarAlCarrito("Sauce Labs Bike Light");
        assertThat(inventario.cantidadEnCarrito()).as("productos en el carrito").isEqualTo(2);

        inventario.quitarDelCarrito("Sauce Labs Backpack");
        assertThat(inventario.cantidadEnCarrito()).as("productos tras quitar uno").isEqualTo(1);
    }

    @Test(groups = "regresion", description = "Ordenar el inventario reordena la lista de verdad")
    @FrameworkAnnotation(autor = "Agustin", categoria = "inventario")
    public void ordenarReordenaLaLista() {
        inventario.ordenarPor("nombre descendente");
        assertThat(inventario.nombresDeProductos())
                .as("nombres tras ordenar descendente")
                .isSortedAccordingTo(Comparator.reverseOrder());

        inventario.ordenarPor("precio ascendente");
        assertThat(inventario.preciosDeProductos())
                .as("precios tras ordenar ascendente")
                .isSorted();
    }

    @Test(groups = {"smoke", "regresion"}, description = "Una compra completa de principio a fin")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"smoke", "checkout"})
    public void compraCompleta() {
        inventario.agregarAlCarrito("Sauce Labs Backpack");
        inventario.abrirCarrito();

        assertThat(checkout.itemsDelCarrito())
                .as("productos listados en el carrito")
                .contains("Sauce Labs Backpack");

        checkout.iniciarCheckout();
        checkout.completarDatos(FakeDataUtils.nombre(), FakeDataUtils.apellido(), "5000");

        assertThat(checkout.total())
                .as("el total deberia ser subtotal mas impuestos")
                .isEqualByComparingTo(checkout.subtotal().add(checkout.impuestos()));

        checkout.confirmar();
        assertThat(checkout.mensajeDeConfirmacion())
                .as("mensaje de confirmacion")
                .isEqualTo("Thank you for your order!");
    }
}
