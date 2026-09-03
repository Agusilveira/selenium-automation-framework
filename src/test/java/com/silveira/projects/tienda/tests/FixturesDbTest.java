package com.silveira.projects.tienda.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseDbTest;
import com.silveira.fixtures.ClientesFixture;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La base como fuente de datos para otros tests.
 *
 * Estos casos no prueban la base: prueban que el fixture entrega lo que promete,
 * en lenguaje de dominio. Un test que los consume pide "un usuario activo" y no
 * escribe una linea de SQL.
 *
 * Es el mismo contrato que ProductosFixture, que trae los datos por API. Que las
 * dos fuentes se usen igual es lo que hace que la abstraccion sirva de algo.
 */
public class FixturesDbTest extends BaseDbTest {

    @Test(groups = "db", description = "El fixture entrega un usuario activo, en lenguaje de dominio")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "fixtures"})
    public void entregaUnUsuarioActivo() {
        String usuario = ClientesFixture.usuarioActivo();

        assertThat(usuario).as("usuario activo entregado por el fixture").isNotBlank();
        // El fixture promete "activo": si devolviera el bloqueado, mentiria.
        assertThat(usuario).isNotEqualTo(ClientesFixture.usuarioInactivo());
    }

    @Test(groups = "db", description = "El fixture distingue al usuario bloqueado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "fixtures"})
    public void distingueAlUsuarioBloqueado() {
        assertThat(ClientesFixture.usuarioInactivo())
                .as("el fixture deberia devolver justamente el cliente dado de baja")
                .isEqualTo("locked_out_user");
    }

    @Test(groups = "db", description = "El fixture encuentra un cliente con compras confirmadas")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "fixtures"})
    public void encuentraUnClienteConComprasConfirmadas() {
        String usuario = ClientesFixture.usuarioConComprasConfirmadas();

        assertThat(usuario)
                .as("cliente con historial de compras, util como precondicion")
                .isEqualTo("standard_user");
    }

    @Test(groups = "db", description = "Los productos del fixture tienen stock de verdad")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "fixtures"})
    public void losProductosDelFixtureTienenStock() {
        List<Map<String, String>> productos = ClientesFixture.productosConStock();

        assertThat(productos).as("productos disponibles").isNotEmpty();
        assertThat(productos)
                .as("un fixture que promete stock no puede devolver productos agotados")
                .allSatisfy(p -> assertThat(Integer.parseInt(p.get("stock"))).isPositive());

        // La forma es la misma que devuelve ExcelHelper, asi que un DataProvider
        // puede cambiar de planilla a base sin que ningun test lo note.
        assertThat(productos.get(0)).containsKeys("nombre", "precio", "stock", "categoria");
    }
}
