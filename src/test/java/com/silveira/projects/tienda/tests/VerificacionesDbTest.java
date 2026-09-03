package com.silveira.projects.tienda.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.common.BaseDbTest;
import com.silveira.db.DatabaseHelper;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consultas de verificación: el tipo de comprobación que una interfaz no puede
 * hacer.
 *
 * Un checkout que muestra "gracias por tu compra" pero no dejó la orden en la base
 * es un test de UI que pasa y un bug que se escapa. Estas son las preguntas que
 * responden eso.
 */
public class VerificacionesDbTest extends BaseDbTest {

    @Test(groups = "db", description = "El esquema quedó creado con las tablas esperadas")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "esquema"})
    public void elEsquemaTieneLasTablasEsperadas() {
        List<Map<String, String>> tablas = DatabaseHelper.consultar(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?", "public");

        assertThat(tablas).extracting(t -> t.get("table_name"))
                .as("tablas del esquema")
                .contains("clientes", "productos", "ordenes", "orden_items");
    }

    @Test(groups = {"db", "smoke"}, description = "El total de una orden coincide con sus ítems")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "integridad"})
    public void elTotalDeLaOrdenCoincideConSusItems() {
        // Esta es la verificación que ninguna pantalla puede hacer por vos: que lo
        // que el sistema muestra como total sea de verdad la suma de las líneas.
        List<Map<String, String>> descuadres = DatabaseHelper.consultar("""
                SELECT o.id,
                       o.total AS total_orden,
                       SUM(i.cantidad * i.precio_unit) AS total_items
                  FROM ordenes o
                  JOIN orden_items i ON i.orden_id = o.id
                 GROUP BY o.id, o.total
                HAVING o.total <> SUM(i.cantidad * i.precio_unit)
                """);

        assertThat(descuadres)
                .as("órdenes cuyo total no coincide con la suma de sus ítems")
                .isEmpty();
    }

    @Test(groups = "db", description = "Un cliente inactivo no tiene órdenes pendientes")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "integridad"})
    public void unClienteInactivoNoTienePendientes() {
        long pendientes = DatabaseHelper.contar("""
                SELECT COUNT(*)
                  FROM ordenes o
                  JOIN clientes c ON c.id = o.cliente_id
                 WHERE c.activo = FALSE AND o.estado = 'pendiente'
                """);

        assertThat(pendientes)
                .as("órdenes pendientes de clientes dados de baja")
                .isZero();
    }

    @Test(groups = "db", description = "Las órdenes canceladas no descuentan stock")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "integridad"})
    public void elProductoSinStockNoSeVende() {
        String sinStock = DatabaseHelper.valorUnico(
                "SELECT nombre FROM productos WHERE stock = 0 LIMIT 1").orElseThrow();

        List<Map<String, String>> ventasActivas = DatabaseHelper.consultar("""
                SELECT o.id
                  FROM orden_items i
                  JOIN productos p ON p.id = i.producto_id
                  JOIN ordenes   o ON o.id = i.orden_id
                 WHERE p.nombre = ? AND o.estado <> 'cancelada'
                """, sinStock);

        assertThat(ventasActivas)
                .as("'%s' no tiene stock, así que no debería estar en órdenes activas", sinStock)
                .isEmpty();
    }

    @Test(groups = "db", description = "Los parámetros van por PreparedStatement, no concatenados")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "seguridad"})
    public void unValorConComillasNoRompeLaConsulta() {
        // En un framework de testing el riesgo no es un ataque: es que un dato con
        // una comilla rompa la consulta y el fallo se lea como un bug de la app.
        List<Map<String, String>> resultado = DatabaseHelper.consultar(
                "SELECT * FROM clientes WHERE usuario = ?", "O'Brien'; DROP TABLE clientes; --");

        assertThat(resultado).as("ningún cliente se llama así").isEmpty();

        // Y la tabla sigue existiendo, que es el punto.
        assertThat(DatabaseHelper.contar("SELECT COUNT(*) FROM clientes"))
                .as("la tabla clientes debería seguir intacta")
                .isPositive();
    }

    @Test(groups = "db", description = "Una transacción que falla no deja datos a medias")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "transacciones"})
    public void unaTransaccionQueFallaSeRevierte() {
        long antes = DatabaseHelper.contar("SELECT COUNT(*) FROM clientes");

        // La segunda sentencia viola la restricción de unicidad a propósito.
        assertThatThrownBy(() -> DatabaseHelper.enTransaccion(List.of(
                "INSERT INTO clientes (usuario, email) VALUES ('nuevo_valido', 'ok@test.com')",
                "INSERT INTO clientes (usuario, email) VALUES ('standard_user', 'duplicado@test.com')"
        ))).hasMessageContaining("se revirtió todo");

        assertThat(DatabaseHelper.contar("SELECT COUNT(*) FROM clientes"))
                .as("el primer INSERT también debería haberse revertido")
                .isEqualTo(antes);
    }

    @Test(groups = "db", description = "Una consulta inválida falla nombrando el SQL")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"db", "diagnostico"})
    public void unaConsultaInvalidaFallaNombrandoElSql() {
        assertThatThrownBy(() -> DatabaseHelper.consultar("SELECT * FROM tabla_que_no_existe"))
                .as("el error debería decir qué consulta falló, no solo que algo falló")
                .hasMessageContaining("tabla_que_no_existe");
    }
}
