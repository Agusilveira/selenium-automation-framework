package com.silveira.db;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.silveira.config.ConfigManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conexión a Cassandra y consultas para verificar datos.
 *
 * Vive al lado de DatabaseManager y no adentro porque Cassandra no habla JDBC:
 * tiene su propio driver, y forzarlo detrás de la misma interfaz escondería las
 * diferencias que importan al escribir una consulta. La principal: en CQL solo se
 * puede filtrar eficientemente por la clave de partición. Una consulta por una
 * columna que no la integra necesita ALLOW FILTERING, que en un cluster real
 * recorre todo, y la respuesta correcta es preguntar por la tabla que sí está
 * particionada por ese dato.
 *
 * Una sesión por corrida, compartida entre hilos: a diferencia de una conexión
 * JDBC, CqlSession es thread-safe y es cara de crear, porque descubre la
 * topología del cluster. Se usa en modo solo lectura: el framework consulta para
 * comparar contra lo que muestran la interfaz y la API, no escribe.
 *
 * Las consultas van siempre con parámetros enlazados, nunca concatenadas: un
 * valor de prueba no puede cambiar la consulta, y el driver reusa el plan.
 */
public final class CassandraManager {

    private static volatile CqlSession sesion;

    private CassandraManager() {
    }

    /**
     * Conecta con lo que diga el perfil activo.
     *
     * Usuario y contraseña son opcionales porque un cluster de test puede no
     * pedirlos. Cuando los pide, llegan por CASSANDRA_USUARIO y CASSANDRA_PASSWORD.
     */
    public static synchronized void conectar() {
        ConfigManager config = ConfigManager.get();
        conectar(puntosDeContacto(config.get("cassandra.contact.points")),
                config.get("cassandra.datacenter"),
                config.get("cassandra.keyspace", ""),
                config.get("cassandra.usuario", ""),
                config.get("cassandra.password", ""));
    }

    /** Conecta con parámetros explícitos. Lo usa el test que levanta un Cassandra propio. */
    public static synchronized void conectar(List<InetSocketAddress> puntos, String datacenter,
                                             String keyspace, String usuario, String password) {
        cerrar();

        int segundos = ConfigManager.get().getInt("cassandra.timeout", 10);
        // El timeout por defecto del driver es de 2 segundos, pensado para una
        // aplicación en la misma red que el cluster. Detrás de una VPN, la
        // primera consulta tarda más que eso y falla por algo que no es un defecto.
        DriverConfigLoader tiempos = DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(segundos))
                .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(segundos))
                .build();

        CqlSessionBuilder constructor = CqlSession.builder()
                .withConfigLoader(tiempos)
                .addContactPoints(puntos)
                .withLocalDatacenter(datacenter);
        if (!keyspace.isBlank()) constructor.withKeyspace(keyspace);
        if (!usuario.isBlank()) constructor.withAuthCredentials(usuario, password);

        try {
            sesion = constructor.build();
            LogUtils.info("Conectado a Cassandra en " + puntos + " (datacenter " + datacenter
                    + (keyspace.isBlank() ? "" : ", keyspace " + keyspace) + ")");
        } catch (RuntimeException e) {
            throw new FrameworkException("No se pudo conectar a Cassandra en " + puntos
                    + " con el datacenter '" + datacenter + "'. Si el nombre del datacenter no "
                    + "coincide con el del cluster, el driver no encuentra nodos y el error "
                    + "no lo dice: verificalo primero.", e);
        }
    }

    /** Filas como mapas columna → valor, en el orden de la consulta. */
    public static List<Map<String, Object>> consultar(String cql, Object... parametros) {
        LogUtils.debug("CQL: " + cql.strip() + " " + Arrays.toString(parametros));
        return sesion().execute(SimpleStatement.newInstance(cql, parametros)).all().stream()
                .map(CassandraManager::comoMapa)
                .toList();
    }

    /**
     * La primera fila, si hay. Para las consultas por clave, que devuelven una o
     * ninguna: vacío es un resultado válido y se afirma en el test, no se lanza acá.
     */
    public static Optional<Map<String, Object>> primera(String cql, Object... parametros) {
        return consultar(cql, parametros).stream().findFirst();
    }

    public static boolean hayConexion() {
        return sesion != null && !sesion.isClosed();
    }

    public static synchronized void cerrar() {
        if (hayConexion()) {
            sesion.close();
            LogUtils.info("Sesión de Cassandra cerrada");
        }
        sesion = null;
    }

    // ------------------------------------------------------------------

    private static CqlSession sesion() {
        if (!hayConexion()) {
            throw new FrameworkException(
                    "No hay sesión de Cassandra. ¿Alguien llamó a CassandraManager.conectar()?");
        }
        return sesion;
    }

    /** "host1:9042,host2:9042". El puerto es opcional y por defecto es 9042. */
    static List<InetSocketAddress> puntosDeContacto(String valor) {
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(punto -> !punto.isEmpty())
                .map(punto -> {
                    int corte = punto.lastIndexOf(':');
                    if (corte < 0) return new InetSocketAddress(punto, 9042);
                    try {
                        return new InetSocketAddress(
                                punto.substring(0, corte), Integer.parseInt(punto.substring(corte + 1)));
                    } catch (NumberFormatException e) {
                        throw new FrameworkException("El punto de contacto '" + punto
                                + "' tiene un puerto que no es un número.", e);
                    }
                })
                .toList();
    }

    private static Map<String, Object> comoMapa(Row fila) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < fila.getColumnDefinitions().size(); i++) {
            ColumnDefinition columna = fila.getColumnDefinitions().get(i);
            mapa.put(columna.getName().asInternal(), fila.getObject(i));
        }
        return mapa;
    }
}
