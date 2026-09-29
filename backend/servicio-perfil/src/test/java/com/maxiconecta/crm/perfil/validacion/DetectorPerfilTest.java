package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-166 · Motor de detección de perfiles incompletos o inconsistentes.
 */
class DetectorPerfilTest {

    private final DetectorPerfil detector = new DetectorPerfil(new ValidadorPerfil());

    @Test
    void unPerfilCompletoYCoherenteQuedaValido() {
        Cliente cliente = clienteBase();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion("D-1", true)));

        detector.detectar(cliente);

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(cliente.getMotivosIncidencia()).isNull();
    }

    @Test
    void unDatoObligatorioVacioQuedaIncompleto() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", null, "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", null);

        detector.detectar(cliente);

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(cliente.getMotivosIncidencia()).isEqualTo("apellidos: vacío");
    }

    @Test
    void unNitConLetrasQuedaInconsistente() {
        Cliente cliente = clienteBase();
        cliente.identificar(cliente.getNombres(), cliente.getApellidos(), "NIT", "ABC1234");

        detector.detectar(cliente);

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(cliente.getMotivosIncidencia()).isEqualTo("numeroDocumento: no coincide con el formato de NIT");
    }

    @Test
    void direccionesActivasSinNingunaPrincipalQuedaInconsistente() {
        Cliente cliente = clienteBase();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion("D-1", false)));

        detector.detectar(cliente);

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(cliente.getMotivosIncidencia()).isEqualTo("direcciones: ninguna dirección principal");
    }

    @Test
    void sinDireccionesLaReglaDePrincipalNoAplica() {
        Cliente cliente = clienteBase();

        detector.detectar(cliente);

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
    }

    private static Cliente clienteBase() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana María", "Pérez Rojas", "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", null);
        return cliente;
    }

    private static Direccion.DatosDireccion direccion(String id, boolean principal) {
        return new Direccion.DatosDireccion(id, TipoDireccion.ENTREGA, "Av. Arce", null, null, "La Paz", null,
                principal);
    }
}
