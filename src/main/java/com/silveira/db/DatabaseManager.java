package com.silveira.db;

import com.silveira.config.ConfigManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Administra la conexión a la base.
 *
 * Con pool y no con conexiones sueltas: una suite paralela abre y cierra
 * conexiones todo el tiempo, y establecer una conexión nueva por consulta agrega
 * más latencia que la consulta misma. El pool también pone un techo: sin él, una
 * suite con 20 hilos puede abrir 20 conexiones y chocar con el límite del motor,
 * que es un fallo confuso y difícil de atribuir.
 *
 * La URL y las credenciales salen de ConfigManager, así que se pueden inyectar por
 * variable de entorno. Nunca se versionan.
 */
public final class DatabaseManager {

    private static HikariDataSource fuente;

    private DatabaseManager() {
    }

    /** Conecta con lo que diga el perfil activo. */
    public static synchronized void conectar() {
        ConfigManager config = ConfigManager.get();
        conectar(config.get("db.url"), config.get("db.user"), config.get("db.password"));
    }

    /**
     * Conecta con parámetros explícitos. Lo usa la suite de base de datos, donde la
     * URL la define el contenedor recién levantado y no se sabe de antemano.
     */
    public static synchronized void conectar(String url, String usuario, String password) {
        cerrar();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(url);
        hikari.setUsername(usuario);
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(ConfigManager.get().getInt("db.pool.size", 5));
        hikari.setConnectionTimeout(ConfigManager.get().getInt("db.timeout", 10) * 1000L);
        hikari.setPoolName("framework-db");

        try {
            fuente = new HikariDataSource(hikari);
            // La primera conexión se pide acá a propósito: si las credenciales o la
            // URL están mal, el error aparece al conectar y no en la mitad de un
            // caso, disfrazado de fallo funcional.
            try (Connection prueba = fuente.getConnection()) {
                LogUtils.info("Conectado a " + prueba.getMetaData().getDatabaseProductName()
                        + " " + prueba.getMetaData().getDatabaseProductVersion());
            }
        } catch (SQLException | RuntimeException e) {
            throw new FrameworkException(
                    "No se pudo conectar a la base en " + url + " con el usuario '" + usuario + "'.", e);
        }
    }

    public static Connection conexion() {
        if (fuente == null || fuente.isClosed()) {
            throw new FrameworkException(
                    "No hay conexión a la base. ¿El test extiende BaseDbTest "
                    + "o alguien llamó a DatabaseManager.conectar()?");
        }
        try {
            return fuente.getConnection();
        } catch (SQLException e) {
            throw new FrameworkException("No se pudo obtener una conexión del pool", e);
        }
    }

    public static boolean hayConexion() {
        return fuente != null && !fuente.isClosed();
    }

    public static synchronized void cerrar() {
        if (fuente != null && !fuente.isClosed()) {
            fuente.close();
            LogUtils.info("Pool de conexiones cerrado");
        }
        fuente = null;
    }
}
