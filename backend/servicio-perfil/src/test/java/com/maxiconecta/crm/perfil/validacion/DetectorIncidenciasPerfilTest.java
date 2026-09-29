package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorIncidenciasPerfilTest {

    private final DetectorPerfil detector = new DetectorPerfil(new ValidadorPerfil());

    @Test
    void unPerfilValidoNoNecesitaDireccionesNiAmbosMediosDeContacto() {
        Cliente cliente = valido();
        var resultado = detector.evaluar(cliente);
        assertThat(resultado.incompleto()).isEmpty();
        assertThat(resultado.inconsistencias()).isEmpty();
    }

    @Test
    void detectaCamposObligatoriosYFormatosSinModificarLosDatos() {
        Cliente cliente = valido();
        cliente.identificar(null, "123", "CI", "!");
        cliente.actualizarContacto("correo-invalido", null);
        var resultado = detector.evaluar(cliente);
        assertThat(resultado.incompleto()).contains("nombres: vacío", "apellidos: formato inválido",
                "numeroDocumento: formato inválido", "email: formato inválido",
                "contacto: se necesita al menos un correo o un teléfono válido");
        assertThat(cliente.getEmail()).isEqualTo("correo-invalido");
        assertThat(cliente.getNumeroDocumento()).isEqualTo("!");
    }

    @Test
    void detectaNitNoNumericoSinDescartarElDocumento() {
        Cliente cliente = valido();
        cliente.identificar("Ana", "Perez", "NIT", "AB-1234");
        var resultado = detector.evaluar(cliente);
        assertThat(resultado.incompleto()).isEmpty();
        assertThat(resultado.inconsistencias()).containsExactly("numeroDocumento: no coincide con el formato de NIT");
        assertThat(cliente.getNumeroDocumento()).isEqualTo("AB-1234");
    }

    @Test
    void noInventaRestriccionesParaOtrosTiposDeDocumento() {
        for (String tipo : List.of("CI", "PASAPORTE", "CE")) {
            Cliente cliente = valido();
            cliente.identificar("Ana", "Perez", tipo, "AB-1234");
            assertThat(detector.evaluar(cliente).inconsistencias()).isEmpty();
        }
    }

    @Test
    void soloLasDireccionesActivasNecesitanUnaPrincipal() {
        Cliente cliente = valido();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion(false)));
        assertThat(detector.evaluar(cliente).inconsistencias())
                .containsExactly("direcciones: ninguna dirección principal");
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of());
        assertThat(detector.evaluar(cliente).inconsistencias()).isEmpty();
    }

    @Test
    void direccionesDeDistintosOrigenesPuedenCompartirIdentificador() {
        Cliente cliente = valido();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion(true)));
        cliente.sincronizarDirecciones(Origen.MARKETPLACE, List.of(direccion(false)));
        assertThat(detector.evaluar(cliente).incompleto()).isEmpty();
        assertThat(detector.evaluar(cliente).inconsistencias()).isEmpty();
    }

    @Test
    void conservaAmbosTiposDeIncidenciaYLosLimpiaAlCorregir() {
        Cliente cliente = valido();
        cliente.identificar(null, "Perez", "NIT", "AB1234");
        var resultado = detector.evaluar(cliente);
        cliente.marcarEstado(resultado.incompleto(), resultado.inconsistencias());
        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(cliente.getMotivosIncompleto()).contains("nombres: vacío");
        assertThat(cliente.getMotivosInconsistencia()).contains("formato de NIT");
        cliente.identificar("Ana", "Perez", "NIT", "123456");
        resultado = detector.evaluar(cliente);
        cliente.marcarEstado(resultado.incompleto(), resultado.inconsistencias());
        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(cliente.getMotivosIncompleto()).isNull();
        assertThat(cliente.getMotivosInconsistencia()).isNull();
    }

    @Test
    void identificaElOrigenDeUnaDireccionIncompleta() {
        Cliente cliente = valido();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(new Direccion.DatosDireccion("D-1",
                TipoDireccion.ENTREGA, "", null, null, "La Paz", null, true)));
        assertThat(detector.evaluar(cliente).incompleto()).containsExactly("VENTAS: direcciones[D-1].calle: vacío");
    }

    private static Cliente valido() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", "Perez", "CI", "1234567");
        cliente.actualizarContacto("ana@correo.com", null);
        return cliente;
    }

    private static Direccion.DatosDireccion direccion(boolean principal) {
        return new Direccion.DatosDireccion("D-1", TipoDireccion.ENTREGA, "Calle Uno", null, null,
                "La Paz", null, principal);
    }
}
