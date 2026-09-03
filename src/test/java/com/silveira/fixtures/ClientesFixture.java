package com.silveira.fixtures;

import com.silveira.db.DatabaseHelper;
import com.silveira.db.DatabaseManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.util.List;
import java.util.Map;

/**
 * Datos de prueba obtenidos de la base.
 *
 * Es el hermano de ProductosFixture, que los trae por API. Lo importante es que
 * ambos exponen lo mismo: métodos en lenguaje de dominio que devuelven datos. El
 * test que los consume no sabe —ni tiene por qué saber— si detrás hay HTTP o SQL.
 *
 * Esa simetría es la que prueba que la abstracción sirve. Si mañana los productos
 * pasan a salir de la base y los clientes de una API, se cambian estos dos
 * archivos y ningún test se entera.
 */
public final class ClientesFixture {

    private ClientesFixture() {
    }

    public static String usuarioActivo() {
        return unicoValor("""
                SELECT usuario FROM clientes
                 WHERE activo = TRUE
                 ORDER BY id
                 LIMIT 1
                """, "no hay ningún cliente activo");
    }

    public static String usuarioInactivo() {
        return unicoValor("""
                SELECT usuario FROM clientes
                 WHERE activo = FALSE
                 ORDER BY id
                 LIMIT 1
                """, "no hay ningún cliente inactivo");
    }

    /** Un cliente que ya tiene órdenes confirmadas: sirve de precondición. */
    public static String usuarioConComprasConfirmadas() {
        return unicoValor("""
                SELECT c.usuario
                  FROM clientes c
                  JOIN ordenes o ON o.cliente_id = c.id
                 WHERE o.estado = 'confirmada'
                 GROUP BY c.usuario
                 ORDER BY COUNT(*) DESC
                 LIMIT 1
                """, "no hay clientes con órdenes confirmadas");
    }

    public static List<Map<String, String>> productosConStock() {
        exigirConexion();
        return DatabaseHelper.consultar("""
                SELECT nombre, precio, stock, categoria
                  FROM productos
                 WHERE stock > 0
                 ORDER BY precio DESC
                """);
    }

    private static String unicoValor(String sql, String queFalta) {
        exigirConexion();
        String valor = DatabaseHelper.valorUnico(sql).orElseThrow(() -> new FrameworkException(
                "El fixture no pudo obtener datos: " + queFalta + ". Esto no es un fallo de la "
                + "aplicación bajo prueba, es una precondición del caso que no se cumplió."));
        LogUtils.info("Fixture desde base: " + valor);
        return valor;
    }

    /**
     * El mensaje distingue las dos causas posibles: que la base no responda, o que
     * el test simplemente no haya establecido la conexión. Sin esa distinción, el
     * segundo caso se diagnostica como el primero y se pierde tiempo mirando
     * infraestructura que está bien.
     */
    private static void exigirConexion() {
        if (!DatabaseManager.hayConexion()) {
            throw new FrameworkException(
                    "El fixture necesita conexión a la base y no hay ninguna abierta. "
                    + "¿El test extiende BaseDbTest?");
        }
    }
}
