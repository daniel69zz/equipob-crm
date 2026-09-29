package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-152 · Datos personales, contacto y direcciones activas del cliente para la ficha integral.
 */
class DatosPersonalesResponseTest {

    @Test
    void tomaLosDatosPersonalesDeContactoYLasDireccionesActivasDelCliente() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", "Perez", "CI", "4455667");
        cliente.actualizarContacto("ana@example.com", "70011122");
        cliente.sincronizarDirecciones(List.of(
                new Direccion.DatosDireccion("D-1", TipoDireccion.ENTREGA, "Av. Arce", "2020", "Sopocachi",
                        "La Paz", null, true)));

        DatosPersonalesResponse respuesta = DatosPersonalesResponse.de(cliente);

        assertThat(respuesta.nombres()).isEqualTo("Ana");
        assertThat(respuesta.apellidos()).isEqualTo("Perez");
        assertThat(respuesta.tipoDocumento()).isEqualTo("CI");
        assertThat(respuesta.numeroDocumento()).isEqualTo("4455667");
        assertThat(respuesta.email()).isEqualTo("ana@example.com");
        assertThat(respuesta.telefono()).isEqualTo("70011122");
        assertThat(respuesta.direcciones()).extracting(PerfilResponse.DireccionResponse::idDireccion)
                .containsExactly("D-1");
    }

    @Test
    void unClienteSinDireccionesActivasTieneLaListaVacia() {
        Cliente cliente = new Cliente();
        cliente.identificar("Bruno", "Rojas", "CI", "1122334");

        DatosPersonalesResponse respuesta = DatosPersonalesResponse.de(cliente);

        assertThat(respuesta.direcciones()).isEmpty();
    }
}
