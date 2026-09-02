package com.silveira.cucumber.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Corre los features sobre TestNG, asi el camino Cucumber comparte listeners,
 * reportes y configuracion con el resto del framework en vez de ser un mundo
 * aparte.
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"com.silveira.cucumber.steps", "com.silveira.cucumber.hooks"},
        plugin = {
                "pretty",
                "html:reports/cucumber-report.html",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
        },
        monochrome = true
)
public class CucumberRunner extends AbstractTestNGCucumberTests {

    /** parallel = true permite correr escenarios en paralelo desde el XML. */
    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
