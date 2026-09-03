package com.silveira.projects.saucedemo.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseTest;
import com.silveira.config.ConfigManager;
import com.silveira.fixtures.ProductosFixture;
import com.silveira.projects.dummyjson.models.Producto;
import com.silveira.projects.saucedemo.pages.InventoryPage;
import com.silveira.projects.saucedemo.pages.LoginPage;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de UI que se alimenta de datos traidos por API.
 *
 * Es el unico punto donde las dos capas se tocan, y lo hacen a traves del fixture:
 * este test no conoce ApiClient, no sabe que endpoint se llamo, y no valida nada
 * de la API. Pide un dato en lenguaje de dominio y lo usa.
 *
 * Si manana ese dato viniera de una base de datos, cambia ProductosFixture y este
 * archivo no se toca.
 */
public class DatosDesdeApiTest extends BaseTest {

    private final LoginPage login = new LoginPage();
    private final InventoryPage inventario = new InventoryPage();

    @Test(groups = "regresion",
          description = "Un flujo de UI usa datos traidos por API sin acoplarse a ella")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"ui", "fixtures"})
    public void unFlujoDeUiUsaDatosTraidosPorApi() {
        // Precondicion: datos que vienen de afuera, no hardcodeados en el test.
        List<Producto> catalogo = ProductosFixture.algunos(3);
        assertThat(catalogo).as("el fixture deberia entregar productos").hasSize(3);

        login.ingresarYEsperarInventario(
                "standard_user",
                ConfigManager.get().get("sauce.password", "secret_sauce"));

        assertThat(inventario.estaCargada()).isTrue();

        // El dato externo se usa para decidir que hace el test: la cantidad de
        // productos a agregar sale del catalogo, no de un numero escrito a mano.
        int aAgregar = Math.min(catalogo.size(), 2);
        List<String> disponibles = inventario.nombresDeProductos();

        for (int i = 0; i < aAgregar; i++) {
            inventario.agregarAlCarrito(disponibles.get(i));
        }

        assertThat(inventario.cantidadEnCarrito())
                .as("productos en el carrito, segun lo que trajo el fixture")
                .isEqualTo(aAgregar);
    }
}
