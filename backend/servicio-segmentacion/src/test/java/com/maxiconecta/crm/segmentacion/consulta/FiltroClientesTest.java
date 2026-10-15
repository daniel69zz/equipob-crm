package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.consulta.OrdenClientes.Direccion;
import com.maxiconecta.crm.segmentacion.segmento.NivelConsumo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-273 · Grupo, filtros y orden del listado de clientes por segmento
 * (docs/segmentacion/criterio-consulta-clientes.md).
 */
class FiltroClientesTest {

    private static final ZoneOffset BOLIVIA = ZoneOffset.ofHours(-4);

    @Test
    void exigeUnSegmentoOUnNivelDeConsumo() {
        assertThatThrownBy(() -> FiltroClientes.de("   ", null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Elija un segmento o un nivel de consumo")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void limpiaElSegmentoYLeeElNivelSinImportarMayusculas() {
        FiltroClientes filtro = FiltroClientes.de("  Alto   valor ", " alto ", null, null);

        assertThat(filtro.segmento()).isEqualTo("Alto valor");
        assertThat(filtro.nivelConsumo()).isEqualTo(NivelConsumo.ALTO);
    }

    @Test
    void sePuedeListarSoloPorNivelDeConsumo() {
        FiltroClientes filtro = FiltroClientes.de(null, "MEDIO", null, null);

        assertThat(filtro.segmento()).isNull();
        assertThat(filtro.nivelConsumo()).isEqualTo(NivelConsumo.MEDIO);
    }

    @Test
    void rechazaUnNivelDeConsumoDesconocido() {
        assertThatThrownBy(() -> FiltroClientes.de("Alto valor", "PLATINO", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Nivel de consumo inválido: 'PLATINO'");
    }

    @Test
    void rechazaUnRangoDeFechasInvertido() {
        assertThatThrownBy(() -> FiltroClientes.de("Alto valor", null, LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no puede ser posterior");
    }

    @Test
    void lasFechasDeUltimaCompraSonDiasCompletos() {
        FiltroClientes filtro = FiltroClientes.de("Alto valor", null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30));

        assertThat(filtro.ultimaCompraDesdeInclusive(BOLIVIA)).isEqualTo("2026-09-01T00:00-04:00");
        assertThat(filtro.ultimaCompraHastaExclusive(BOLIVIA)).isEqualTo("2026-10-01T00:00-04:00");
        assertThat(FiltroClientes.de("Alto valor", null, null, null).ultimaCompraDesdeInclusive(BOLIVIA)).isNull();
    }

    @Test
    void sinOrdenSeListaPorValorDeMayorAMenor() {
        assertThat(OrdenClientes.de(null)).isEqualTo(OrdenClientes.VALOR);
        assertThat(Direccion.de(" ")).isEqualTo(Direccion.DESC);
    }

    @Test
    void elOrdenSePideConElNombreDelParametro() {
        assertThat(OrdenClientes.de("ultimaCompra")).isEqualTo(OrdenClientes.ULTIMA_COMPRA);
        assertThat(OrdenClientes.de("NIVELCONSUMO")).isEqualTo(OrdenClientes.NIVEL_CONSUMO);
        assertThat(Direccion.de("Asc")).isEqualTo(Direccion.ASC);
        assertThatThrownBy(() -> OrdenClientes.de("nombre"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Orden inválido: 'nombre'")
                .hasMessageContaining("ultimaCompra");
        assertThatThrownBy(() -> Direccion.de("arriba")).hasMessageContaining("se espera asc o desc");
    }

    @Test
    void losClientesSinDatoVanAlFinalYSeDesempataPorCliente() {
        assertThat(OrdenClientes.ULTIMA_COMPRA.ordenSql(Direccion.ASC))
                .isEqualTo("ultima_compra ASC NULLS LAST, cliente_id ASC");
        assertThat(OrdenClientes.VALOR.ordenSql(Direccion.DESC)).isEqualTo("valor DESC NULLS LAST, cliente_id ASC");
    }
}
