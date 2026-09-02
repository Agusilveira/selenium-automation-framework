package com.silveira.helpers;

import com.silveira.config.FrameworkConstants;
import com.silveira.exceptions.FrameworkException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelHelperTest {

    private static final String FIXTURE = FrameworkConstants.RUTA_DATA + "usuarios.xlsx";

    /**
     * Genera el Excel de datos si no existe. Se versiona una vez generado, y así el
     * repo no depende de que alguien recuerde crear un binario a mano.
     */
    @BeforeClass
    public void generarFixtureSiFalta() {
        if (FileHelper.existe(FIXTURE)) return;

        ExcelHelper.escribirHoja(FIXTURE, "usuarios", List.of(
                Map.of("usuario", "standard_user", "password", "secret_sauce", "resultado", "exito"),
                Map.of("usuario", "locked_out_user", "password", "secret_sauce", "resultado", "error"),
                Map.of("usuario", "standard_user", "password", "clave_mala", "resultado", "error")
        ));
    }

    @Test
    public void leeLaHojaUsandoLaPrimeraFilaComoEncabezados() {
        List<Map<String, String>> filas = ExcelHelper.leerHoja(FIXTURE, "usuarios");

        assertThat(filas).hasSize(3);
        assertThat(filas.get(0)).containsKeys("usuario", "password", "resultado");
        assertThat(filas.get(0).get("usuario")).isEqualTo("standard_user");
        assertThat(filas.get(1).get("resultado")).isEqualTo("error");
    }

    @Test
    public void entregaElFormatoQueEsperaTestNG() {
        Object[][] datos = ExcelHelper.comoDataProvider(FIXTURE, "usuarios");

        assertThat(datos).hasNumberOfRows(3);
        assertThat(datos[0][0]).isInstanceOf(Map.class);
    }

    @Test
    public void unaHojaInexistenteFallaNombrandola() {
        assertThatThrownBy(() -> ExcelHelper.leerHoja(FIXTURE, "no-existe"))
                .isInstanceOf(FrameworkException.class)
                .hasMessageContaining("no-existe");
    }

    @Test
    public void conservaLosCerosALaIzquierdaYNoUsaNotacionCientifica() {
        String temporal = "target/pruebas-helpers/formatos.xlsx";
        ExcelHelper.escribirHoja(temporal, "datos", List.of(
                Map.of("codigoPostal", "05000", "telefono", "1122334455")
        ));

        Map<String, String> fila = ExcelHelper.leerHoja(temporal, "datos").get(0);
        assertThat(fila.get("codigoPostal")).isEqualTo("05000");
        assertThat(fila.get("telefono")).isEqualTo("1122334455");
    }

    @Test
    public void descartaLasFilasVacias() {
        String temporal = "target/pruebas-helpers/vacias.xlsx";
        ExcelHelper.escribirHoja(temporal, "datos", List.of(
                Map.of("a", "uno"),
                Map.of("a", ""),
                Map.of("a", "dos")
        ));

        assertThat(ExcelHelper.leerHoja(temporal, "datos")).hasSize(2);
    }
}
