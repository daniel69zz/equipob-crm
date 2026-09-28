package com.maxiconecta.crm.perfil.cliente;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-554 · Mantenimiento de las direcciones del cliente.
 */
class ClienteDireccionesTest {

    @Test
    void agregaActualizaYDesactivaLasDireccionesDeUnOrigen() {
        Cliente cliente = new Cliente();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion("D-1", "Av. Arce", true),
                direccion("D-2", "Calle Sucre", false)));

        List<CambioCampo> cambios = cliente.sincronizarDirecciones(Origen.VENTAS,
                List.of(direccion("D-1", "Av. Arce 2020", true)));

        assertThat(cambios).extracting(CambioCampo::campo)
                .containsExactly("direcciones[D-1].calle", "direcciones[D-2].activa");
        assertThat(cliente.getDireccionesActivas()).extracting(Direccion::getIdDireccionOrigen).containsExactly("D-1");
        assertThat(cliente.getDirecciones()).hasSize(2);
    }

    @Test
    void lasDireccionesDeOtroOrigenNoSeTocan() {
        Cliente cliente = new Cliente();
        cliente.sincronizarDirecciones(Origen.MARKETPLACE, List.of(direccion("addr-1", "Av. Blanco Galindo", true)));

        cliente.sincronizarDirecciones(Origen.VENTAS, List.of());

        assertThat(cliente.getDireccionesActivas()).extracting(Direccion::getIdDireccionOrigen).containsExactly("addr-1");
    }

    @Test
    void soloUnaDireccionQuedaComoPrincipal() {
        Cliente cliente = new Cliente();

        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion("D-1", "Av. Arce", true),
                direccion("D-2", "Calle Sucre", true)));

        assertThat(cliente.getDirecciones()).filteredOn(Direccion::isPrincipal)
                .extracting(Direccion::getIdDireccionOrigen).containsExactly("D-1");
    }

    @Test
    void unaDireccionQueVuelveAInformarseSeReactiva() {
        Cliente cliente = new Cliente();
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(direccion("D-1", "Av. Arce", true)));
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of());

        List<CambioCampo> cambios = cliente.sincronizarDirecciones(Origen.VENTAS,
                List.of(direccion("D-1", "Av. Arce", true)));

        assertThat(cambios).extracting(CambioCampo::campo)
                .containsExactly("direcciones[D-1].principal", "direcciones[D-1].activa");
        assertThat(cliente.getDireccionesActivas()).hasSize(1);
    }

    private static Direccion.DatosDireccion direccion(String id, String calle, boolean principal) {
        return new Direccion.DatosDireccion(id, TipoDireccion.ENTREGA, calle, null, null, "La Paz", null, principal);
    }
}
