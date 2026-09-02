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
            driver.get("https://www.saucedemo.com");
            assertThat(driver.getTitle()).isEqualTo("Swag Labs");
            assertThat(DriverManager.get()).isSameAs(driver);
        } finally {
            DriverManager.quit();
        }
        assertThat(DriverManager.hayDriver()).isFalse();
    }
}
