package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * SCRUM-307 · Filtros de rango de fechas, periodo, categorías y periodo base (SCRUM-302,
 * docs/compra/evolucion-consumo-categoria.md#filtros-scrum-302).
 */
class FiltroEvolucionConsumoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 8);

    // --- Rango de fechas ---

    @Test
    void sinFechasElRangoSonLosUltimosDoceMeses() {
        FiltroEvolucionConsumo filtro = crear(null, null, null);

        assertThat(filtro.desde()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(filtro.hasta()).isEqualTo(HOY);
        assertThat(filtro.periodo()).isEqualTo(TipoPeriodo.MENSUAL);
        assertThat(filtro.periodos()).hasSize(12);
        assertThat(filtro.periodos().get(0).clave()).isEqualTo("2025-11");
        assertThat(filtro.periodos().get(11))
                .isEqualTo(new Periodo("2026-10", LocalDate.of(2026, 10, 1), HOY, true));
    }

    @Test
    void conSoloDesdeElRangoLlegaHastaHoy() {
        FiltroEvolucionConsumo filtro = crear(LocalDate.of(2026, 9, 1), null, null);

        assertThat(filtro.hasta()).isEqualTo(HOY);
        assertThat(filtro.periodos()).extracting(Periodo::clave).containsExactly("2026-09", "2026-10");
    }

    @Test
    void desdePosteriorAHastaSeRechaza() {
        assertThatThrownBy(() -> crear(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La fecha 'desde' no puede ser posterior a 'hasta'");
    }

    @Test
    void recortaElPrimerYElUltimoPeriodoAlRangoYLosMarcaComoParciales() {
        FiltroEvolucionConsumo filtro = crear(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), null);

        assertThat(filtro.periodos()).extracting(Periodo::clave, Periodo::inicio, Periodo::fin, Periodo::parcial)
                .containsExactly(
                        tuple("2026-01", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 31), true),
                        tuple("2026-02", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), false),
                        tuple("2026-03", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), true));
    }

    @Test
    void masDeSesentaPeriodosSeRechazanPeroUnPeriodoMasAmplioLosAdmite() {
        LocalDate desde = LocalDate.of(2020, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);

        assertThatThrownBy(() -> crear(desde, hasta, "MENSUAL"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("84 periodos mensuales")
                .hasMessageContaining("el máximo es 60");
        assertThat(crear(desde, hasta, "TRIMESTRAL").periodos()).hasSize(28);
        assertThat(crear(desde, hasta, "anual").periodos()).extracting(Periodo::clave)
                .containsExactly("2020", "2021", "2022", "2023", "2024", "2025", "2026");
    }

    // --- Tipo de periodo ---

    @Test
    void agrupaPorTrimestresConSuClave() {
        FiltroEvolucionConsumo filtro = crear(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 11, 20), " trimestral ");

        assertThat(filtro.periodos()).extracting(Periodo::clave, Periodo::inicio, Periodo::fin)
                .containsExactly(
                        tuple("2026-T1", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 31)),
                        tuple("2026-T2", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)),
                        tuple("2026-T3", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)),
                        tuple("2026-T4", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 20)));
    }

    @Test
    void unPeriodoDesconocidoSeRechaza() {
        assertThatThrownBy(() -> crear(null, null, "SEMANAL"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Periodo desconocido: 'SEMANAL' (se espera MENSUAL, TRIMESTRAL o ANUAL)");
    }

    // --- Categorías ---

    @Test
    void quitaLosEspaciosYLasCategoriasRepetidas() {
        FiltroEvolucionConsumo filtro = FiltroEvolucionConsumo.crear(null, null, null,
                List.of(" Hogar", "Hogar ", "Hogar, jardín"), null, HOY);

        assertThat(filtro.categorias()).containsExactlyInAnyOrder("Hogar", "Hogar, jardín");
    }

    @Test
    void sinCategoriasSeMuestranTodas() {
        assertThat(crear(null, null, null).categorias()).isEmpty();
    }

    @Test
    void unaCategoriaVaciaODemasiadoLargaSeRechaza() {
        assertThatThrownBy(() -> FiltroEvolucionConsumo.crear(null, null, null, List.of(" "), null, HOY))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La categoría del filtro no puede estar vacía");
        assertThatThrownBy(() -> FiltroEvolucionConsumo.crear(null, null, null, Arrays.asList((String) null), null, HOY))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> FiltroEvolucionConsumo.crear(null, null, null, List.of("x".repeat(101)), null, HOY))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("supera los 100 caracteres");
    }

    @Test
    void masDeCincuentaCategoriasSeRechazan() {
        List<String> categorias = IntStream.rangeClosed(1, 51).mapToObj(i -> "Categoría " + i).toList();

        assertThatThrownBy(() -> FiltroEvolucionConsumo.crear(null, null, null, categorias, null, HOY))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Se pueden filtrar hasta 50 categorías");
    }

    // --- Periodo base ---

    @Test
    void sinPeriodoBaseSeComparaConElPrimeroDelRango() {
        FiltroEvolucionConsumo filtro = crear(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(filtro.periodoBase()).isEqualTo("2026-07");
        assertThat(filtro.indiceBase()).isZero();
    }

    @Test
    void elPeriodoBaseDebeSerUnPeriodoDelRango() {
        FiltroEvolucionConsumo filtro = FiltroEvolucionConsumo.crear(LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30), null, null, " 2026-08 ", HOY);
        assertThat(filtro.periodoBase()).isEqualTo("2026-08");
        assertThat(filtro.indiceBase()).isEqualTo(1);

        assertThatThrownBy(() -> FiltroEvolucionConsumo.crear(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30),
                null, null, "2026-10", HOY))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El periodo base '2026-10' no está dentro del rango consultado (de 2026-07 a 2026-09)");
    }

    private static FiltroEvolucionConsumo crear(LocalDate desde, LocalDate hasta, String periodo) {
        return FiltroEvolucionConsumo.crear(desde, hasta, periodo, null, null, HOY);
    }
}
