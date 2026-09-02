package com.silveira.config;

import com.silveira.enums.Browser;
import com.silveira.exceptions.ConfigKeyMissingException;
import com.silveira.exceptions.FrameworkException;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigManagerTest {

    private ConfigManager con(Properties propiedades, Map<String, String> overrides) {
        return new ConfigManager(propiedades, overrides::get);
    }

    private Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    public void usaElArchivoCuandoNoHayOverride() {
        ConfigManager config = con(props("base.url", "https://del-archivo"), Map.of());
        assertThat(config.get("base.url")).isEqualTo("https://del-archivo");
    }

    @Test
    public void elOverrideLeGanaAlArchivo() {
        ConfigManager config = con(props("base.url", "https://del-archivo"),
                                   Map.of("BASE_URL", "https://del-entorno"));
        assertThat(config.get("base.url")).isEqualTo("https://del-entorno");
    }

    @Test
    public void convierteLaClaveAFormatoDeVariableDeEntorno() {
        ConfigManager config = con(props("page.load.timeout", "30"),
                                   Map.of("PAGE_LOAD_TIMEOUT", "5"));
        assertThat(config.getInt("page.load.timeout")).isEqualTo(5);
    }

    @Test
    public void unOverrideVacioNoPisaElArchivo() {
        ConfigManager config = con(props("base.url", "https://del-archivo"),
                                   Map.of("BASE_URL", "   "));
        assertThat(config.get("base.url")).isEqualTo("https://del-archivo");
    }

    @Test
    public void unaClaveFaltanteFallaNombrandola() {
        ConfigManager config = con(props(), Map.of());
        assertThatThrownBy(() -> config.get("base.url"))
                .isInstanceOf(ConfigKeyMissingException.class)
                .hasMessageContaining("base.url")
                .hasMessageContaining("BASE_URL");
    }

    @Test
    public void unNumeroInvalidoFallaNombrandoLaClave() {
        ConfigManager config = con(props("explicit.timeout", "quince"), Map.of());
        assertThatThrownBy(() -> config.getInt("explicit.timeout"))
                .isInstanceOf(FrameworkException.class)
                .hasMessageContaining("explicit.timeout");
    }

    @Test
    public void leeBooleanos() {
        ConfigManager config = con(props("headless", "true"), Map.of());
        assertThat(config.headless()).isTrue();
    }

    @Test
    public void resuelveElNavegadorComoEnum() {
        ConfigManager config = con(props("browser", "chrome"), Map.of());
        assertThat(config.browser()).isEqualTo(Browser.CHROME);
    }

    @Test
    public void unNavegadorDesconocidoFalla() {
        ConfigManager config = con(props("browser", "netscape"), Map.of());
        assertThatThrownBy(config::browser)
                .isInstanceOf(FrameworkException.class)
                .hasMessageContaining("netscape");
    }

    @Test
    public void losDefaultsAplicanSoloSiFaltaLaClave() {
        ConfigManager config = con(props(), Map.of());
        assertThat(config.explicitTimeout()).isEqualTo(FrameworkConstants.TIMEOUT_EXPLICITO_DEFAULT);
        assertThat(config.get("no.existe", "reserva")).isEqualTo("reserva");
    }
}
