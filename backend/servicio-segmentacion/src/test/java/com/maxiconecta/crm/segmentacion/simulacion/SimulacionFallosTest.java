package com.maxiconecta.crm.segmentacion.simulacion;

import com.maxiconecta.crm.segmentacion.criterio.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulacionFallosTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CriterioService reglas = mock(CriterioService.class);
    private final CriterioRepository criterios = mock(CriterioRepository.class);
    private final SolicitudSimulacion solicitud = new SolicitudSimulacion(null,
            new SolicitudCriterio("Nueva", "", Criterio.Indicador.VALOR, Criterio.Condicion.ENTRE,
                    BigDecimal.ZERO, BigDecimal.TEN, null, null));
    private final Criterio propuesta = new Criterio(Long.MAX_VALUE, "Nueva", "", Criterio.Indicador.VALOR,
            Criterio.Condicion.ENTRE, BigDecimal.ZERO, BigDecimal.TEN, null, true, 0, null, null, null);

    @Test void falloDeBaseDevuelve503SinExponerDetalleTecnico() {
        when(reglas.prepararSimulacion(null, solicitud.regla())).thenReturn(propuesta);
        when(criterios.activos()).thenReturn(List.of());
        doThrow(new DataAccessResourceFailureException("detalle privado de conexión"))
                .when(jdbc).query(any(PreparedStatementCreator.class), any(RowCallbackHandler.class));
        assertThatThrownBy(() -> new SimulacionSegmentacion(jdbc, reglas, criterios, 100, 10).simular(solicitud))
                .hasMessageContaining("503").hasMessageNotContaining("detalle privado");
    }

    @Test void tiempoExcesivoNoDevuelveUnResultadoParcial() {
        when(reglas.prepararSimulacion(null, solicitud.regla())).thenAnswer(invocacion -> {
            Thread.sleep(1100); return propuesta;
        });
        when(criterios.activos()).thenReturn(List.of());
        when(jdbc.query(any(PreparedStatementCreator.class), org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any())).thenReturn(0L);
        assertThatThrownBy(() -> new SimulacionSegmentacion(jdbc, reglas, criterios, 100, 1).simular(solicitud))
                .hasMessageContaining("503").hasMessageContaining("tiempo");
    }

    @Test void limitesOperativosInvalidosSeRechazanAlArrancar() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulacionSegmentacion(jdbc, reglas, criterios, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulacionSegmentacion(jdbc, reglas, criterios, 100, 0));
    }
}
