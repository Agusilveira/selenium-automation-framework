package com.silveira.common;

import com.silveira.config.ConfigManager;
import com.silveira.db.DatabaseManager;
import com.silveira.driver.DriverManager;
import com.silveira.driver.TargetFactory;
import com.silveira.exceptions.FrameworkException;
import com.silveira.keywords.AlertUtils;
import com.silveira.utils.LogUtils;
import io.restassured.RestAssured;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Base de los tests contra la aplicación propia, la única cuyas tres caras
 * controlamos.
 *
 * Junta lo que hasta acá vivía separado: navegador de `BaseTest`, HTTP de
 * `BaseApiTest` y conexión de `BaseDbTest`. No es una excepción al criterio de
 * mantenerlas separadas, es la consecuencia de tenerlas: cuando un test necesita
 * las tres, las tres están disponibles y se comportan igual que siempre.
 *
 * La conexión a la base se abre una vez por suite; el navegador, uno por método.
 */
public abstract class BaseAppTest {

    @BeforeSuite(alwaysRun = true)
    public void conectarALaBase() {
        ConfigManager config = ConfigManager.get();
        try {
            DatabaseManager.conectar();
            LogUtils.info("Aplicación bajo prueba en " + config.baseUrl());
        } catch (RuntimeException e) {
            throw new FrameworkException("""
                    No se pudo conectar a la base de la aplicacion bajo prueba.
                    Levantala con:
                      docker compose -f docker-compose.app.yml up -d
                      ./scripts/preparar-app.sh""", e);
        }
    }

    @AfterSuite(alwaysRun = true)
    public void desconectarDeLaBase() {
        DatabaseManager.cerrar();
    }

    @BeforeMethod(alwaysRun = true)
    public void abrirNavegador() {
        ConfigManager config = ConfigManager.get();
        // RestAssured queda apuntando a la API de esta aplicación, no a la del
        // proyecto de API. Sin esto, un test que corre después de la suite de
        // DummyJSON le pegaria al servidor equivocado.
        RestAssured.reset();
        DriverManager.set(TargetFactory.crear());
        LogUtils.info("Navegador listo para " + config.baseUrl());
    }

    @AfterMethod(alwaysRun = true)
    public void cerrarNavegador() {
        if (!DriverManager.hayDriver()) return;
        AlertUtils.aceptarSiHay();
        DriverManager.quit();
    }
}
