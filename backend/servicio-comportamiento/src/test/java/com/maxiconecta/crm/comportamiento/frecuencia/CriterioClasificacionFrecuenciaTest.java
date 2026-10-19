package com.maxiconecta.crm.comportamiento.frecuencia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-339 · Casos límite de la clasificación por frecuencia con los umbrales por defecto (4 y 10). */
class CriterioClasificacionFrecuenciaTest {

    private final CriterioClasificacionFrecuencia criterio = new CriterioClasificacionFrecuencia(4, 10);

    @Test
    void sinComprasVigentesNoHayClasificacion() {
        assertThat(criterio.clasificar(0)).isEmpty();
        assertThat(criterio.clasificar(-1)).isEmpty();
        assertThat(ClasificacionFrecuenciaCliente.de(0, criterio))
                .isEqualTo(new ClasificacionFrecuenciaCliente(null, 0, true));
    }

    @Test
    void deUnaATresComprasEsOcasional() {
        assertThat(criterio.clasificar(1)).contains(ClasificacionFrecuencia.OCASIONAL);
        assertThat(criterio.clasificar(3)).contains(ClasificacionFrecuencia.OCASIONAL);
    }

    @Test
    void elValorExactamenteEnCadaUmbralPerteneceALaClasificacionSuperior() {
        assertThat(criterio.clasificar(4)).contains(ClasificacionFrecuencia.HABITUAL);
        assertThat(criterio.clasificar(9)).contains(ClasificacionFrecuencia.HABITUAL);
        assertThat(criterio.clasificar(10)).contains(ClasificacionFrecuencia.FRECUENTE);
        assertThat(criterio.clasificar(250)).contains(ClasificacionFrecuencia.FRECUENTE);
    }

    @Test
    void elClienteInformaLaFrecuenciaQueDeterminaSuClasificacion() {
        assertThat(ClasificacionFrecuenciaCliente.de(5, criterio))
                .isEqualTo(new ClasificacionFrecuenciaCliente(ClasificacionFrecuencia.HABITUAL, 5, false));
    }

    @Test
    void losUmbralesInvalidosImpidenArrancar() {
        assertThatThrownBy(() -> new CriterioClasificacionFrecuencia(1, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioClasificacionFrecuencia(10, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioClasificacionFrecuencia(12, 10)).hasMessageContaining("habitual");
    }

    @Test
    void losUmbralesSonConfigurables() {
        CriterioClasificacionFrecuencia otro = new CriterioClasificacionFrecuencia(2, 5);
        assertThat(otro.clasificar(1)).contains(ClasificacionFrecuencia.OCASIONAL);
        assertThat(otro.clasificar(2)).contains(ClasificacionFrecuencia.HABITUAL);
        assertThat(otro.clasificar(5)).contains(ClasificacionFrecuencia.FRECUENTE);
    }
}
