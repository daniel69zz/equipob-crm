package com.maxiconecta.crm.segmentacion.asignacion;

import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-260 · Regla de prioridad entre criterios, con los ejemplos de docs/segmentacion/asignacion-automatica.md. */
class ReglaPrioridadSegmentoTest {

    private static final Criterio OCASIONALES = numerico(1, "Ocasionales", Indicador.FRECUENCIA, "1", "3");
    private static final Criterio HABITUALES = numerico(2, "Habituales", Indicador.FRECUENCIA, "4", "9");
    private static final Criterio FRECUENTES = numerico(3, "Frecuentes", Indicador.FRECUENCIA, "10", null);
    private static final Criterio ALTO_VALOR = numerico(4, "Alto valor", Indicador.VALOR, "5000", null);
    private static final Criterio TECNOLOGIA = categoria(5, "Tecnología", "Electrónica");
    private static final Criterio LECTORES = categoria(6, "Lectores", "Libros");
    private static final Criterio INFANTIL = categoria(7, "Infantil", "Juguetes");
    private static final List<Criterio> TODOS = List.of(OCASIONALES, HABITUALES, FRECUENTES, ALTO_VALOR,
            TECNOLOGIA, LECTORES, INFANTIL);

    @Test
    void elValorTienePrioridadSobreLaFrecuenciaYLaCategoria() {
        assertThat(elegir(12, "6200", "Electrónica")).contains(ALTO_VALOR);
    }

    @Test
    void laFrecuenciaTienePrioridadSobreLaCategoria() {
        assertThat(elegir(5, "300", "Libros")).contains(HABITUALES);
    }

    @Test
    void entreCategoriasGanaLaDeMayorConsumo() {
        assertThat(elegir(0, "0", "Libros", "Juguetes")).contains(LECTORES);
        assertThat(elegir(0, "0", "Juguetes", "Libros")).contains(INFANTIL);
    }

    @Test
    void laCategoriaSeComparaSinAcentosNiMayusculas() {
        assertThat(elegir(0, "0", "  ELECTRONICA ")).contains(TECNOLOGIA);
    }

    @Test
    void conEmpateGanaElCriterioMasAntiguo() {
        Criterio libros2 = categoria(9, "Lectores 2", "libros");
        assertThat(ReglaPrioridadSegmento.elegir(List.of(libros2, LECTORES), 0, BigDecimal.ZERO, List.of("Libros")))
                .contains(LECTORES);
    }

    @Test
    void sinCriteriosQueSeCumplanNoHaySegmento() {
        assertThat(elegir(0, "0")).isEmpty();
        assertThat(elegir(0, "0", "Hogar")).isEmpty();
    }

    @Test
    void losCriteriosInactivosNoParticipan() {
        Criterio inactivo = new Criterio(4L, "Alto valor", "", Indicador.VALOR, Condicion.MAYOR_IGUAL,
                new BigDecimal("5000"), null, null, false, 1, null, null, "admin");
        assertThat(ReglaPrioridadSegmento.elegir(List.of(inactivo, FRECUENTES), 12, new BigDecimal("6200"), List.of()))
                .contains(FRECUENTES);
    }

    @Test
    void losLimitesSonInclusivos() {
        assertThat(elegir(3, "0")).contains(OCASIONALES);
        assertThat(elegir(4, "0")).contains(HABITUALES);
        assertThat(elegir(9, "4999.99")).contains(HABITUALES);
        assertThat(elegir(9, "5000")).contains(ALTO_VALOR);
    }

    private static Optional<Criterio> elegir(long frecuencia, String valor, String... categorias) {
        return ReglaPrioridadSegmento.elegir(TODOS, frecuencia, new BigDecimal(valor), List.of(categorias));
    }

    private static Criterio numerico(long id, String nombre, Indicador indicador, String minimo, String maximo) {
        Condicion condicion = maximo == null ? Condicion.MAYOR_IGUAL : Condicion.ENTRE;
        return new Criterio(id, nombre, "", indicador, condicion, new BigDecimal(minimo),
                maximo == null ? null : new BigDecimal(maximo), null, true, 0, null, null, "admin");
    }

    private static Criterio categoria(long id, String nombre, String categoria) {
        return new Criterio(id, nombre, "", Indicador.CATEGORIA, Condicion.CONTIENE, null, null, categoria, true, 0,
                null, null, "admin");
    }
}
