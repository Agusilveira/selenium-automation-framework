package com.silveira.db;

import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas contra la base.
 *
 * Devuelve List<Map<String,String>>, la misma forma que ExcelHelper. No es
 * casualidad: significa que un DataProvider puede pasar de leer una planilla a
 * leer la base sin que ningún test se entere de que cambió la fuente.
 *
 * Todo va por PreparedStatement con parámetros, nunca concatenando. En un
 * framework de testing el riesgo no es que alguien ataque: es que un dato con una
 * comilla rompa la consulta y el fallo se lea como un problema de la aplicación.
 */
public final class DatabaseHelper {

    private DatabaseHelper() {
    }

    /**
     * Ejecuta una consulta y devuelve las filas.
     *
     * Los valores se convierten a String para que el resultado sea uniforme y
     * comparable sin importar el tipo de la columna. Cuando hace falta el tipo
     * original, están valorUnico y las consultas tipadas.
     */
    public static List<Map<String, String>> consultar(String sql, Object... parametros) {
        LogUtils.info("SQL: " + resumir(sql) + (parametros.length > 0
                ? " con " + parametros.length + " parámetro(s)" : ""));

        try (Connection conexion = DatabaseManager.conexion();
             PreparedStatement sentencia = preparar(conexion, sql, parametros);
             ResultSet resultado = sentencia.executeQuery()) {

            List<Map<String, String>> filas = new ArrayList<>();
            ResultSetMetaData meta = resultado.getMetaData();
            int columnas = meta.getColumnCount();

            while (resultado.next()) {
                Map<String, String> fila = new LinkedHashMap<>();
                for (int i = 1; i <= columnas; i++) {
                    Object valor = resultado.getObject(i);
                    fila.put(meta.getColumnLabel(i), valor == null ? null : String.valueOf(valor));
                }
                filas.add(fila);
            }

            LogUtils.info("La consulta devolvió " + filas.size() + " fila(s)");
            return filas;

        } catch (SQLException e) {
            throw new FrameworkException("Falló la consulta: " + resumir(sql), e);
        }
    }

    /** Primera columna de la primera fila. Vacío si la consulta no devolvió nada. */
    public static Optional<String> valorUnico(String sql, Object... parametros) {
        List<Map<String, String>> filas = consultar(sql, parametros);
        if (filas.isEmpty()) return Optional.empty();
        return Optional.ofNullable(filas.get(0).values().iterator().next());
    }

    /** Atajo para los COUNT(*), que es la consulta de verificación más común. */
    public static long contar(String sql, Object... parametros) {
        return valorUnico(sql, parametros)
                .map(Long::parseLong)
                .orElseThrow(() -> new FrameworkException(
                        "La consulta de conteo no devolvió ningún valor: " + resumir(sql)));
    }

    public static boolean existe(String sql, Object... parametros) {
        return !consultar(sql, parametros).isEmpty();
    }

    /** INSERT, UPDATE o DELETE. Devuelve cuántas filas se vieron afectadas. */
    public static int ejecutar(String sql, Object... parametros) {
        LogUtils.info("SQL (escritura): " + resumir(sql));
        try (Connection conexion = DatabaseManager.conexion();
             PreparedStatement sentencia = preparar(conexion, sql, parametros)) {

            int afectadas = sentencia.executeUpdate();
            LogUtils.info("Filas afectadas: " + afectadas);
            return afectadas;

        } catch (SQLException e) {
            throw new FrameworkException("Falló la sentencia: " + resumir(sql), e);
        }
    }

    /** Varias sentencias en un script, separadas por punto y coma. */
    public static void ejecutarScript(String script) {
        try (Connection conexion = DatabaseManager.conexion();
             java.sql.Statement sentencia = conexion.createStatement()) {
            sentencia.execute(script);
            LogUtils.info("Script ejecutado (" + script.length() + " caracteres)");
        } catch (SQLException e) {
            throw new FrameworkException("Falló el script SQL", e);
        }
    }

    /**
     * Ejecuta varias sentencias como una sola transacción, revirtiendo todo si
     * alguna falla.
     *
     * Sin esto, un test que prepara datos en tres pasos y falla en el segundo deja
     * la base a medio armar, y el test siguiente falla por un motivo que no tiene
     * nada que ver con lo que estaba probando.
     */
    public static void enTransaccion(List<String> sentencias) {
        try (Connection conexion = DatabaseManager.conexion()) {
            boolean autoCommitPrevio = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try {
                for (String sql : sentencias) {
                    try (PreparedStatement s = conexion.prepareStatement(sql)) {
                        s.executeUpdate();
                    }
                }
                conexion.commit();
                LogUtils.info("Transacción confirmada: " + sentencias.size() + " sentencia(s)");
            } catch (SQLException e) {
                conexion.rollback();
                LogUtils.warn("Transacción revertida por un fallo: " + e.getMessage());
                throw new FrameworkException("Falló la transacción, se revirtió todo", e);
            } finally {
                conexion.setAutoCommit(autoCommitPrevio);
            }
        } catch (SQLException e) {
            throw new FrameworkException("No se pudo manejar la transacción", e);
        }
    }

    private static PreparedStatement preparar(Connection conexion, String sql, Object... parametros)
            throws SQLException {
        PreparedStatement sentencia = conexion.prepareStatement(sql);
        for (int i = 0; i < parametros.length; i++) {
            sentencia.setObject(i + 1, parametros[i]);
        }
        return sentencia;
    }

    /** El SQL en el log en una línea: un script largo lo vuelve ilegible. */
    private static String resumir(String sql) {
        String limpio = sql.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 160 ? limpio : limpio.substring(0, 160) + "...";
    }
}
