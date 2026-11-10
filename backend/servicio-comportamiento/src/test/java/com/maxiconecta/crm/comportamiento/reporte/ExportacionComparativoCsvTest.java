package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.TendenciaConsumo;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Periodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-499 · Formato del CSV del reporte comparativo (SCRUM-62) y SCRUM-498 · variación de un indicador
 * cuando un periodo no tiene base.
 */
class ExportacionComparativoCsvTest {

    private final ExportacionComparativoCsv csv = new ExportacionComparativoCsv();

    @Test
    void escribeLosValoresTalCualConBomCrlfYSinNotacionCientifica() {
        String texto = csv.exportar(reporte(null, List.of(
                Indicador.de("VALOR_TOTAL", "Valor total (Bs)", new BigDecimal("250.00"), new BigDecimal("1180.00")))));

        assertThat(texto).startsWith(ExportacionComparativoCsv.BOM + "Reporte comparativo");
        assertThat(texto).contains("\r\n").doesNotContain("E+");
        assertThat(texto).contains("Alcance,Toda la cartera\r\n");
        assertThat(texto).contains("Periodo A,2026-08-01 a 2026-08-31\r\n");
        assertThat(texto).contains("Indicador,Codigo,Periodo A,Periodo B,Variacion,Variacion %,Tendencia\r\n");
        assertThat(texto).contains("Valor total (Bs),VALOR_TOTAL,250.00,1180.00,930.00,372.00,CRECE\r\n");
    }

    @Test
    void entrecomillaLosCamposConComasOComillas() {
        String texto = csv.exportar(reporte("Alto valor, \"VIP\"", List.of()));

        assertThat(texto).contains("Alcance,\"Segmento Alto valor, \"\"VIP\"\"\"\r\n");
    }

    @Test
    void unPromedioSinBaseDejaLaVariacionVaciaEnLugarDeUnCero() {
        Indicador ticket = Indicador.de("TICKET_PROMEDIO", "Ticket promedio (Bs)", null, new BigDecimal("125.00"));

        assertThat(ticket.variacionAbsoluta()).isNull();
        assertThat(ticket.tendencia()).isNull();
        assertThat(csv.exportar(reporte(null, List.of(ticket))))
                .contains("Ticket promedio (Bs),TICKET_PROMEDIO,,125.00,,,\r\n");
    }

    @Test
    void unConteoQueEmpiezaEnCeroEsNuevoYNoTienePorcentaje() {
        Indicador compras = Indicador.de("COMPRAS", "Compras", BigDecimal.ZERO, new BigDecimal("4"));

        assertThat(compras.tendencia()).isEqualTo(TendenciaConsumo.NUEVO);
        assertThat(compras.variacionAbsoluta()).isEqualByComparingTo("4");
        assertThat(compras.variacionPorcentual()).isNull();
    }

    private static ReporteComparativo reporte(String segmento, List<Indicador> indicadores) {
        return new ReporteComparativo(OffsetDateTime.parse("2026-11-10T12:00:00Z"),
                new Periodo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                new Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "America/La_Paz",
                new ReporteComportamiento.Alcance(segmento, segmento == null ? null : 2L), false, null, indicadores);
    }
}
