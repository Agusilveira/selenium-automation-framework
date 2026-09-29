package com.silveira.common;

import com.silveira.config.ConfigManager;
import com.silveira.db.CassandraManager;
import com.silveira.driver.DriverManager;
import com.silveira.driver.TargetFactory;
import com.silveira.keywords.AlertUtils;
import com.silveira.keywords.WebUI;
import com.silveira.api.AuthManager;
import com.silveira.utils.LogUtils;
import io.restassured.RestAssured;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Base de los tests que cruzan las tres capas de la aplicación: interfaz,
 * servicios y Cassandra.
 *
 * Junta lo que en el resto del framework vive separado —navegador de BaseTest,
 * HTTP de BaseApiTest— más la sesión de Cassandra. No es una excepción al
 * criterio de mantenerlas separadas, es la consecuencia de tenerlas: cuando un
 * test necesita las tres, están disponibles y se comportan igual que siempre.
 *
 * La sesión de Cassandra se abre una vez por suite, porque es cara de crear y es
 * thread-safe. El navegador, uno por método, como en BaseTest.
 */
public abstract class BaseAppTest {

    @BeforeSuite(alwaysRun = true)
    public void conectarACassandra() {
        CassandraManager.conectar();
        LogUtils.info("Aplicación bajo prueba en " + ConfigManager.get().baseUrl());
    }

    @AfterSuite(alwaysRun = true)
    public void desconectarDeCassandra() {
        CassandraManager.cerrar();
    }

    @BeforeMethod(alwaysRun = true)
    public void abrirNavegador() {
        // RestAssured guarda configuración estática. Sin esto, lo que dejó otro
        // test en la misma JVM le cambia el destino a este.
        RestAssured.reset();
        DriverManager.set(TargetFactory.crear());
        WebUI.abrirUrl(ConfigManager.get().baseUrl());
    }

    @AfterMethod(alwaysRun = true)
    public void cerrarNavegador() {
        AuthManager.limpiar();
        if (!DriverManager.hayDriver()) return;
        AlertUtils.aceptarSiHay();
        DriverManager.quit();
    }
}
