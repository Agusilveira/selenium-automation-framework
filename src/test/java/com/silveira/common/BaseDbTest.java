package com.silveira.common;

import com.silveira.config.ConfigManager;
import com.silveira.db.DatabaseHelper;
import com.silveira.db.DatabaseManager;
import com.silveira.db.SqlLoader;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * Base de los tests de base de datos. Sin navegador.
 *
 * Levanta un Postgres real y descartable con Testcontainers. Real y no H2 porque
 * un framework que solo sabe hablar con una base embebida no prueba mucho: el
 * dialecto, los tipos y el comportamiento transaccional son los del motor que se
 * usa en produccion.
 *
 * Un contenedor por suite y no por caso: levantarlo cuesta unos segundos, y
 * multiplicarlo por cada test convertiria una suite de segundos en una de minutos.
 * El aislamiento entre casos se consigue con transacciones y datos propios, no
 * tirando la base abajo cada vez.
 *
 * Si Docker no esta disponible, los casos se marcan OMITIDOS con el motivo, no
 * fallidos: la precondicion de infraestructura no se cumplio, y eso no es lo mismo
 * que un defecto.
 */
public abstract class BaseDbTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeSuite(alwaysRun = true)
    public void levantarBase() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("tienda")
                    .withUsername("framework")
                    .withPassword("framework");
            postgres.start();

            LogUtils.info("Postgres levantado en " + postgres.getJdbcUrl());
            DatabaseManager.conectar(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());

            DatabaseHelper.ejecutarScript(SqlLoader.cargar("esquema"));
            DatabaseHelper.ejecutarScript(SqlLoader.cargar("datos"));
            LogUtils.info("Esquema y datos sembrados");

        } catch (RuntimeException | NoClassDefFoundError e) {
            String motivo = "No se pudo levantar Postgres con Testcontainers, probablemente "
                    + "porque Docker no esta disponible. Los tests de base de datos requieren "
                    + "Docker; el resto de las suites no. Motivo: " + e.getMessage();

            // En un entorno donde la base es obligatoria (CI), omitir seria peor que
            // fallar: la suite quedaria en verde sin haber probado nada, que es la
            // misma mentira que un testFailureIgnore. Donde no es obligatoria (la
            // maquina de alguien sin Docker), omitir con el motivo es lo correcto.
            if (ConfigManager.get().getBool("db.requerida")) {
                throw new FrameworkException(
                        "La base de datos es obligatoria en este perfil y no se pudo levantar. "
                        + motivo, e);
            }
            LogUtils.warn("Omitiendo los tests de base de datos. " + motivo);
            throw new SkipException(motivo, e);
        }
    }

    @AfterSuite(alwaysRun = true)
    public void bajarBase() {
        DatabaseManager.cerrar();
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
            LogUtils.info("Contenedor de Postgres detenido");
        }
    }
}
