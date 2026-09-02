package com.silveira.helpers;

import com.silveira.exceptions.ConfigKeyMissingException;
import com.silveira.exceptions.InvalidPathException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelpersTest {

    private static final String TEMPORAL = "target/pruebas-helpers";

    @AfterClass
    public void limpiar() {
        FileHelper.borrar(TEMPORAL);
        PropertiesHelper.limpiarCache();
    }

    // --- FileHelper ---

    @Test
    public void escribeYLeeTexto() {
        String ruta = TEMPORAL + "/nota.txt";
        FileHelper.escribirTexto(ruta, "hola framework");
        assertThat(FileHelper.leerTexto(ruta)).isEqualTo("hola framework");
        assertThat(FileHelper.existe(ruta)).isTrue();
    }

    @Test
    public void unaRutaInexistenteFallaNombrandola() {
        assertThatThrownBy(() -> FileHelper.exigir("no/existe/nada.txt"))
                .isInstanceOf(InvalidPathException.class)
                .hasMessageContaining("nada.txt");
    }

    @Test
    public void listaPorExtension() {
        FileHelper.escribirTexto(TEMPORAL + "/listado/a.json", "{}");
        FileHelper.escribirTexto(TEMPORAL + "/listado/b.json", "{}");
        FileHelper.escribirTexto(TEMPORAL + "/listado/c.txt", "x");

        assertThat(FileHelper.listar(TEMPORAL + "/listado", ".json")).hasSize(2);
    }

    // --- JsonHelper ---

    @Test
    public void leeUnArrayDeObjetosComoLista() {
        List<Map<String, Object>> usuarios =
                JsonHelper.comoLista("src/test/resources/data/usuarios.json");

        assertThat(usuarios).hasSize(3);
        assertThat(usuarios.get(0)).containsEntry("usuario", "standard_user");
        assertThat(usuarios).extracting(m -> m.get("resultado"))
                .containsExactly("exito", "error", "exito");
    }

    @Test
    public void escribeYRelaeJson() {
        String ruta = TEMPORAL + "/salida.json";
        JsonHelper.escribir(ruta, Map.of("clave", "valor"));
        assertThat(JsonHelper.comoMapa(ruta)).containsEntry("clave", "valor");
    }

    @Test
    public void unJsonInvalidoFalla() {
        String ruta = TEMPORAL + "/roto.json";
        FileHelper.escribirTexto(ruta, "{ esto no es json");
        assertThatThrownBy(() -> JsonHelper.comoMapa(ruta))
                .hasMessageContaining("roto.json");
    }

    // --- PropertiesHelper ---

    @Test
    public void resuelveLocatorsDesdeObjects() {
        assertThat(PropertiesHelper.locator("login.boton"))
                .isEqualTo("css:[data-test='login-button']");
    }

    @Test
    public void unLocatorInexistenteFallaNombrandolo() {
        assertThatThrownBy(() -> PropertiesHelper.locator("login.no.existe"))
                .isInstanceOf(ConfigKeyMissingException.class)
                .hasMessageContaining("login.no.existe");
    }
}
