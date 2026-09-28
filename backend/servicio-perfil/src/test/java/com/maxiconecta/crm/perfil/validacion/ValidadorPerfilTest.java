package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRecibido;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-554 · Un dato obligatorio vacío o mal formado deja el perfil incompleto (criterio 3).
 */
class ValidadorPerfilTest {

    private final ValidadorPerfil validador = new ValidadorPerfil();

    @Test
    void conTodosLosDatosValidosElPerfilQuedaCompleto() {
        PerfilValidado perfil = validador.validar(datos("Ana María", "Pérez Rojas", "ci", "4455667",
                "ana@correo.com", "+591 70012345", List.of(direccion("D-1", "Av. Arce", "La Paz"))));

        assertThat(perfil.completo()).isTrue();
        assertThat(perfil.tipoDocumento()).isEqualTo("CI");
        assertThat(perfil.direcciones()).singleElement()
                .satisfies(d -> assertThat(d.tipo()).isEqualTo(TipoDireccion.ENTREGA));
    }

    @Test
    void unDatoObligatorioVacioQuedaComoMotivo() {
        PerfilValidado perfil = validador.validar(datos("Ana", "  ", "CI", null, "ana@correo.com", null, List.of()));

        assertThat(perfil.completo()).isFalse();
        assertThat(perfil.motivos()).containsExactly("apellidos: vacío", "numeroDocumento: vacío");
        assertThat(perfil.apellidos()).isNull();
    }

    @Test
    void unDatoMalFormadoNoSeGuardaYQuedaComoMotivo() {
        PerfilValidado perfil = validador.validar(datos("Ana 2", "Pérez", "LICENCIA", "44*55", "ana@", "12",
                List.of()));

        assertThat(perfil.motivos()).containsExactly(
                "nombres: formato inválido",
                "tipoDocumento: valor no válido (LICENCIA)",
                "numeroDocumento: formato inválido",
                "email: formato inválido",
                "telefono: formato inválido",
                "contacto: se necesita al menos un correo o un teléfono válido");
        assertThat(perfil.nombres()).isNull();
        assertThat(perfil.email()).isNull();
        assertThat(perfil.telefono()).isNull();
    }

    @Test
    void bastaUnMedioDeContactoValido() {
        assertThat(validador.validar(datos("Ana", "Pérez", "CI", "4455667", null, "70012345", List.of())).completo())
                .isTrue();
        assertThat(validador.validar(datos("Ana", "Pérez", "CI", "4455667", "ana@correo.com", null, List.of())).completo())
                .isTrue();
    }

    @Test
    void unaDireccionSinCiudadNoSeGuardaYDejaElPerfilIncompleto() {
        PerfilValidado perfil = validador.validar(datos("Ana", "Pérez", "CI", "4455667", "ana@correo.com", null,
                List.of(direccion("D-1", "Av. Arce", "La Paz"), direccion("D-9", "Calle Sucre", null),
                        direccion("D-1", "Repetida", "La Paz"), direccion(null, "Sin código", "La Paz"))));

        assertThat(perfil.direcciones()).extracting(d -> d.idDireccionOrigen()).containsExactly("D-1");
        assertThat(perfil.motivos()).containsExactly("direcciones[D-9].ciudad: vacío",
                "direcciones[D-1].idDireccion: repetido", "direcciones[3].idDireccion: vacío");
    }

    private static EventoClienteRecibido.DatosCliente datos(String nombres, String apellidos, String tipoDocumento,
                                                            String numeroDocumento, String email, String telefono,
                                                            List<EventoClienteRecibido.DatosDireccion> direcciones) {
        return new EventoClienteRecibido.DatosCliente("CLI-1", null, nombres, apellidos, tipoDocumento, numeroDocumento,
                new EventoClienteRecibido.Contacto(email, telefono), direcciones);
    }

    private static EventoClienteRecibido.DatosDireccion direccion(String id, String calle, String ciudad) {
        return new EventoClienteRecibido.DatosDireccion(id, "ENTREGA", calle, null, null, ciudad, null, false);
    }
}
