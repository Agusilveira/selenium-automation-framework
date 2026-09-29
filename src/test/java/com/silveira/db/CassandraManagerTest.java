package com.silveira.db;

import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import org.testcontainers.cassandra.CassandraContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CassandraManager contra un Cassandra real y descartable.
 *
 * Real y no simulado por lo mismo que la base de datos: lo que se prueba es que
 * el driver, la configuración de tiempos y la conversión de filas funcionen con
 * el motor de verdad. Tarda alrededor de un minuto en arrancar, por eso corre en
 * una suite aparte y no en la de unitarios.
 *
 * La tabla imita el caso de uso: una fila por CUIT, particionada por CUIT.
 */
public class CassandraManagerTest {

    private CassandraContainer cassandra;

    @BeforeClass(alwaysRun = true)
    public void levantarCassandra() {
        try {
            cassandra = new CassandraContainer("cassandra:5.0");
            cassandra.start();
        } catch (RuntimeException | NoClassDefFoundError e) {
            throw new SkipException("No se pudo levantar Cassandra con Testcontainers, "
                    + "probablemente porque Docker no está disponible: " + e.getMessage(), e);
        }

        CassandraManager.conectar(List.of(cassandra.getContactPoint()),
                cassandra.getLocalDatacenter(), "", "", "");
        CassandraManager.consultar("""
                CREATE KEYSPACE IF NOT EXISTS padron
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}""");
        CassandraManager.consultar("""
                CREATE TABLE IF NOT EXISTS padron.personas (
                    cuit text PRIMARY KEY,
                    razon_social text,
                    actividad int)""");
        CassandraManager.consultar(
                "INSERT INTO padron.personas (cuit, razon_social, actividad) VALUES (?, ?, ?)",
                "20111111112", "Persona de Prueba", 620100);
        LogUtils.info("Cassandra de prueba lista");
    }

    @AfterClass(alwaysRun = true)
    public void bajarCassandra() {
        CassandraManager.cerrar();
        if (cassandra != null) cassandra.stop();
    }

    @Test
    public void devuelveLaFilaComoMapaConLosTiposDelMotor() {
        Map<String, Object> fila = CassandraManager.primera(
                "SELECT cuit, razon_social, actividad FROM padron.personas WHERE cuit = ?",
                "20111111112").orElseThrow();

        assertThat(fila).containsExactly(
                Map.entry("cuit", "20111111112"),
                Map.entry("razon_social", "Persona de Prueba"),
                Map.entry("actividad", 620100));
    }

    @Test
    public void unCuitInexistenteEsVacioYNoUnError() {
        assertThat(CassandraManager.primera(
                "SELECT cuit FROM padron.personas WHERE cuit = ?", "20999999990")).isEmpty();
    }

    /** El parámetro enlazado se trata como dato: no puede alterar la consulta. */
    @Test
    public void unParametroMaliciosoNoCambiaLaConsulta() {
        assertThat(CassandraManager.consultar(
                "SELECT cuit FROM padron.personas WHERE cuit = ?",
                "x' OR cuit = '20111111112")).isEmpty();
    }

    @Test
    public void unPuntoDeContactoSinPuertoUsaElDeCassandra() {
        assertThat(CassandraManager.puntosDeContacto("localhost, localhost:9142"))
                .extracting(punto -> punto.getPort())
                .containsExactly(9042, 9142);
    }

    @Test
    public void unPuertoInvalidoFallaNombrandoElPunto() {
        assertThatThrownBy(() -> CassandraManager.puntosDeContacto("localhost:abc"))
                .isInstanceOf(FrameworkException.class)
                .hasMessageContaining("localhost:abc");
    }
}
