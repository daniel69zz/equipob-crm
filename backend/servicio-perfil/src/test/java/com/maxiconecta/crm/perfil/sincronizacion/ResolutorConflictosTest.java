package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.CampoOrigen;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.configuracion.PoliticaConflictos;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-163 · Regla de prioridad ante valores distintos de dos sistemas (criterio 3).
 */
class ResolutorConflictosTest {

    private static final OffsetDateTime AYER = OffsetDateTime.parse("2026-09-27T10:00:00-04:00");
    private static final OffsetDateTime HOY = OffsetDateTime.parse("2026-09-28T10:00:00-04:00");

    private final Cliente actual = clienteDeVentas();
    private final Map<String, CampoOrigen> puestoPorVentas = Map.of(
            "nombres", new CampoOrigen(1L, "nombres", Origen.VENTAS, AYER),
            "email", new CampoOrigen(1L, "email", Origen.VENTAS, AYER));

    @Test
    void enIdentificacionGanaElSistemaDeMayorPrioridad() {
        ResolutorConflictos.Resolucion resolucion = new ResolutorConflictos(new PoliticaConflictos(null))
                .resolver(actual, cambio("Anita", null, Set.of("nombres")), Origen.MARKETPLACE, HOY, puestoPorVentas, 9L);

        assertThat(resolucion.propuesta().nombres()).isEqualTo("Ana");
        assertThat(resolucion.conflictos()).singleElement().satisfies(c -> {
            assertThat(c.getDecision()).isEqualTo(ConflictoPerfil.Decision.CONSERVADO);
            assertThat(c.getRegla()).isEqualTo(ConflictoPerfil.Regla.PRIORIDAD_SISTEMA);
            assertThat(c.getValorActual()).isEqualTo("Ana");
            assertThat(c.getValorRecibido()).isEqualTo("Anita");
            assertThat(c.getIdEvento()).isEqualTo(9L);
        });
    }

    @Test
    void laPrioridadSeConfigura() {
        ResolutorConflictos marketplacePrimero = new ResolutorConflictos(
                new PoliticaConflictos(List.of(Origen.MARKETPLACE, Origen.VENTAS)));

        ResolutorConflictos.Resolucion resolucion = marketplacePrimero.resolver(actual,
                cambio("Anita", null, Set.of("nombres")), Origen.MARKETPLACE, HOY, puestoPorVentas, 9L);

        assertThat(resolucion.propuesta().nombres()).isEqualTo("Anita");
        assertThat(resolucion.conflictos()).singleElement()
                .extracting(ConflictoPerfil::getDecision).isEqualTo(ConflictoPerfil.Decision.APLICADO);
    }

    @Test
    void enContactoGanaElCambioMasReciente() {
        ResolutorConflictos resolutor = new ResolutorConflictos(new PoliticaConflictos(null));

        ResolutorConflictos.Resolucion posterior = resolutor.resolver(actual,
                cambio(null, "nuevo@correo.com", Set.of("email")), Origen.MARKETPLACE, HOY, puestoPorVentas, 9L);
        ResolutorConflictos.Resolucion anterior = resolutor.resolver(actual,
                cambio(null, "viejo@correo.com", Set.of("email")), Origen.MARKETPLACE, AYER.minusDays(1),
                puestoPorVentas, 9L);

        assertThat(posterior.propuesta().contacto().email()).isEqualTo("nuevo@correo.com");
        assertThat(posterior.conflictos().get(0).getDecision()).isEqualTo(ConflictoPerfil.Decision.APLICADO);
        assertThat(anterior.propuesta().contacto().email()).isEqualTo("ana@correo.com");
        assertThat(anterior.conflictos().get(0).getRegla()).isEqualTo(ConflictoPerfil.Regla.MAS_RECIENTE);
    }

    @Test
    void noHayConflictoConElMismoSistemaNiCuandoElCampoEstabaVacio() {
        ResolutorConflictos resolutor = new ResolutorConflictos(new PoliticaConflictos(null));

        assertThat(resolutor.resolver(actual, cambio("Anita", null, Set.of("nombres")), Origen.VENTAS, HOY,
                puestoPorVentas, 9L).conflictos()).isEmpty();
        assertThat(resolutor.resolver(actual, cambio(null, null, Set.of("telefono")), Origen.MARKETPLACE, HOY,
                puestoPorVentas, 9L).conflictos()).isEmpty();
    }

    private static Cliente clienteDeVentas() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", "Pérez", "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", null);
        return cliente;
    }

    /** Propuesta ya combinada con el perfil actual: los campos no informados traen el valor actual. */
    private EventoClienteRecibido.DatosCliente cambio(String nombres, String email, Set<String> informados) {
        return new EventoClienteRecibido.DatosCliente("mp-1", null,
                informados.contains("nombres") ? nombres : actual.getNombres(), actual.getApellidos(),
                actual.getTipoDocumento(), actual.getNumeroDocumento(),
                new EventoClienteRecibido.Contacto(informados.contains("email") ? email : actual.getEmail(),
                        informados.contains("telefono") ? "+59171122333" : actual.getTelefono()),
                List.of(), informados);
    }
}
