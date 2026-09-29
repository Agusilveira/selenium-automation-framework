package com.silveira.driver;

import com.silveira.enums.Browser;
import com.silveira.enums.Target;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverFactoryTest {

    @Test
    public void creaChromeHeadlessYNavega() {
        WebDriver driver = TargetFactory.crear(Target.LOCAL, Browser.CHROME, true);
        DriverManager.set(driver);
        try {
            // Una página en línea y no un sitio real: esto prueba que el driver se
            // crea y navega, no que haya red ni que un tercero esté arriba.
            driver.get("data:text/html,<title>Framework</title>");
            assertThat(driver.getTitle()).isEqualTo("Framework");
            assertThat(DriverManager.get()).isSameAs(driver);
        } finally {
            DriverManager.quit();
        }
        assertThat(DriverManager.hayDriver()).isFalse();
    }
}
