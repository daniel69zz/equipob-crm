package com.maxiconecta.crm.perfil.sincronizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.Origen;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-554 · Lectura de los eventos de cliente del simulador según el contrato RIO-CRM-01.
 */
class LectorEventosClienteTest {

    private final LectorEventosCliente lector = new LectorEventosCliente(new ObjectMapper());

    @Test
    void leeElAltaDeVentasConIdentificacionContactoYDirecciones() {
        EventoClienteRecibido evento = lector.leer(ejemplo("cliente-ventas-alta.json"));

        assertThat(evento.tipo()).isEqualTo(TipoEventoCliente.CLIENTE_REGISTRADO);
        assertThat(evento.origen()).isEqualTo(Origen.VENTAS);
        assertThat(evento.responsableDelCambio()).isEqualTo("vendedor.jperez");
        assertThat(evento.cliente().idCliente()).isEqualTo("CLI-5521");
        assertThat(evento.cliente().nombres()).isEqualTo("Ana María");
        assertThat(evento.cliente().contacto().email()).isEqualTo("ana.perez@correo.com");
        assertThat(evento.cliente().direcciones()).singleElement()
                .satisfies(d -> {
                    assertThat(d.idDireccion()).isEqualTo("D-1");
                    assertThat(d.principal()).isTrue();
                });
        assertThat(evento.fechaCambio()).isEqualTo(OffsetDateTime.parse("2026-09-20T10:15:00-04:00"));
    }

    @Test
    void sinResponsableSeRegistraLaSincronizacionAutomatica() {
        EventoClienteRecibido evento = lector.leer(ejemplo("cliente-marketplace-alta.json"));

        assertThat(evento.origen()).isEqualTo(Origen.MARKETPLACE);
        assertThat(evento.responsableDelCambio()).isEqualTo(EventoClienteRecibido.RESPONSABLE_POR_DEFECTO);
    }

    @Test
    void sinFechaDeActualizacionSeOrdenaPorLaFechaDeEmision() {
        String sinFecha = ejemplo("cliente-ventas-alta.json")
                .replace("\"fechaActualizacion\": \"2026-09-20T10:15:00-04:00\",", "");

        assertThat(lector.leer(sinFecha).fechaCambio()).isEqualTo(OffsetDateTime.parse("2026-09-20T10:15:02-04:00"));
    }

    @Test
    void losDatosDelPerfilSeLeenAunqueFalten() {
        EventoClienteRecibido evento = lector.leer(ejemplo("cliente-incompleto.json"));

        assertThat(evento.cliente().numeroDocumento()).isEmpty();
        assertThat(evento.cliente().contacto().email()).isEqualTo("luisa.fernandez@");
    }

    @Test
    void unDocumentoNumericoSeLeeComoTexto() {
        String numerico = ejemplo("cliente-ventas-alta.json").replace("\"4455667\"", "4455667");

        assertThat(lector.leer(numerico).cliente().numeroDocumento()).isEqualTo("4455667");
    }

    @Test
    void sinIdentificadorDeClienteElEventoEsIlegible() {
        assertThatThrownBy(() -> lector.leer(ejemplo("cliente-sin-id.json")))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("cliente.idCliente");
    }

    @Test
    void unOrigenOTipoDesconocidoEsIlegible() {
        assertThatThrownBy(() -> lector.leer(ejemplo("cliente-ventas-alta.json").replace("\"VENTAS\"", "\"TIENDA\"")))
                .hasMessageContaining("'origen' debe ser MARKETPLACE o VENTAS");
        assertThatThrownBy(() -> lector.leer(ejemplo("cliente-ventas-alta.json")
                .replace("CLIENTE_REGISTRADO", "CLIENTE_BORRADO")))
                .hasMessageContaining("'tipoEvento'");
    }

    @Test
    void unaFechaMalEscritaOUnMensajeQueNoEsJsonSonIlegibles() {
        assertThatThrownBy(() -> lector.leer(ejemplo("cliente-ventas-alta.json")
                .replace("2026-09-20T10:15:00-04:00", "20/09/2026")))
                .hasMessageContaining("'cliente.fechaActualizacion' debe ser una fecha");
        assertThatThrownBy(() -> lector.leer("no es json"))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("no es un JSON válido");
    }

    @Test
    void unObjetoDondeSeEsperaUnValorSimpleEsIlegible() {
        assertThatThrownBy(() -> lector.leer(ejemplo("cliente-ventas-alta.json")
                .replace("\"nombres\": \"Ana María\"", "\"nombres\": {\"primero\": \"Ana\"}")))
                .hasMessageContaining("'nombres' debe ser un valor simple");
    }

    @Test
    void laCabeceraIdentificaElMensajeAunqueSeaIlegible() {
        LectorEventosCliente.Cabecera cabecera = lector.cabecera(ejemplo("cliente-sin-id.json"));

        assertThat(cabecera.idEvento()).isEqualTo("3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f");
        assertThat(cabecera.origen()).isEqualTo("VENTAS");
        assertThat(cabecera.idClienteOrigen()).isNull();
        assertThat(lector.cabecera("no es json")).isEqualTo(LectorEventosCliente.Cabecera.VACIA);
    }
}
