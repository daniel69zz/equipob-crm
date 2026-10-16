package com.maxiconecta.crm.comportamiento.preferencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SCRUM-371 · El disparador decide qué mensajes actualizan el perfil, cuáles se reintentan y cuáles van a respaldo. */
class ReceptorPreferenciasTest {

    private static final String VALIDO = """
            {"idEvento":"5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c","tipoEventoOrigen":"COMPRA_CONFIRMADA","idCliente":42,
             "identificadoresOrigen":["CLI-5521"],"frecuencia":1,"valor":350.50,"categorias":["Electrónica"],
             "calculadasEn":"2026-10-16T12:00:00Z"}
            """;

    private final PerfilesPreferencias perfiles = mock(PerfilesPreferencias.class);
    private final ReceptorPreferencias receptor = new ReceptorPreferencias(perfiles,
            new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void unMensajeValidoActualizaElPerfilDelClienteConSuEvento() {
        when(perfiles.actualizarPorEvento(anyLong(), anyString(), anyString())).thenReturn(
                new PerfilesPreferencias.Actualizacion(PerfilesPreferencias.Resultado.ACTUALIZADO,
                        PerfilPreferencias.pendiente(42)));

        receptor.recibir(mensaje(VALIDO));

        verify(perfiles).actualizarPorEvento(42, "5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", "COMPRA_CONFIRMADA");
    }

    @Test
    void unMensajeInvalidoSeRechazaSinReintentoNiActualizacion() {
        for (String contenido : new String[] {"{ no es json", VALIDO.replace("\"idCliente\":42,", ""),
                VALIDO.replace("COMPRA_CONFIRMADA", "PAGO"), VALIDO.replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", "")}) {
            assertThatThrownBy(() -> receptor.recibir(mensaje(contenido)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(perfiles);
    }

    @Test
    void siElServicioDePerfilNoRespondeSeReintenta() {
        when(perfiles.actualizarPorEvento(anyLong(), anyString(), anyString()))
                .thenThrow(new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE, "sin perfil"));

        assertThatThrownBy(() -> receptor.recibir(mensaje(VALIDO))).isInstanceOf(PreferenciasException.class);
    }

    @Test
    void unClienteInexistenteOConsumoInconsistenteVaARespaldoSinReintento() {
        for (HttpStatus estado : new HttpStatus[] {HttpStatus.NOT_FOUND, HttpStatus.CONFLICT}) {
            doThrow(new PreferenciasException(estado, "rechazo"))
                    .when(perfiles).actualizarPorEvento(anyLong(), anyString(), anyString());
            assertThatThrownBy(() -> receptor.recibir(mensaje(VALIDO)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
    }

    private static Message mensaje(String contenido) {
        return new Message(contenido.getBytes(StandardCharsets.UTF_8));
    }
}
