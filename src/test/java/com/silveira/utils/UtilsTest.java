package com.silveira.utils;

import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {

    @Test
    public void elTimestampSirveComoNombreDeArchivo() {
        String timestamp = DateUtils.timestampParaArchivo();
        assertThat(timestamp).doesNotContain("/", ":", " ");
        assertThat(timestamp).matches("[0-9]{8}-[0-9]{6}-[0-9]{3}");
    }

    @Test
    public void sumaYRestaDias() {
        String patron = "yyyy-MM-dd";
        DateTimeFormatter formato = DateTimeFormatter.ofPattern(patron);

        assertThat(DateUtils.sumarDias(7, patron))
                .isEqualTo(LocalDate.now().plusDays(7).format(formato));
        assertThat(DateUtils.restarDias(1, patron))
                .isEqualTo(LocalDate.now().minusDays(1).format(formato));
    }

    @Test
    public void losEmailsGeneradosNoSeRepiten() {
        assertThat(FakeDataUtils.email()).isNotEqualTo(FakeDataUtils.email());
        assertThat(FakeDataUtils.email()).contains("@");
    }

    @Test
    public void elNumeroCaeEnElRangoPedido() {
        for (int i = 0; i < 20; i++) {
            assertThat(FakeDataUtils.numeroEntre(5, 10)).isBetween(5, 10);
        }
    }
}
