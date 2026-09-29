package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRecibido;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Evalúa los datos guardados sin modificarlos, según el catálogo de SCRUM-168. */
@Component
public class DetectorIncidenciasPerfil {

    private final ValidadorPerfil validador;

    public DetectorIncidenciasPerfil(ValidadorPerfil validador) {
        this.validador = validador;
    }

    public Evaluacion evaluar(Cliente cliente) {
        List<Direccion> activas = cliente.getDireccionesActivas();
        PerfilValidado datos = validador.validar(datos(cliente, List.of()));
        Set<String> incompleto = new LinkedHashSet<>(datos.motivos());
        // Los identificadores de dirección son únicos dentro de cada sistema de origen.
        for (Origen origen : Origen.values()) {
            List<EventoClienteRecibido.DatosDireccion> direcciones = activas.stream()
                    .filter(d -> d.getOrigen() == origen)
                    .map(d -> new EventoClienteRecibido.DatosDireccion(d.getIdDireccionOrigen(), d.getTipo().name(),
                            d.getCalle(), d.getNumero(), d.getZona(), d.getCiudad(), d.getReferencia(), d.isPrincipal()))
                    .toList();
            validador.validar(datos(cliente, direcciones)).motivos().stream()
                    .filter(m -> m.startsWith("direcciones["))
                    .map(m -> origen + ": " + m)
                    .forEach(incompleto::add);
        }
        List<String> inconsistencias = new ArrayList<>();
        if ("NIT".equals(datos.tipoDocumento()) && datos.numeroDocumento() != null
                && !datos.numeroDocumento().matches("[0-9]+")) {
            inconsistencias.add("numeroDocumento: no coincide con el formato de NIT");
        }
        if (!activas.isEmpty() && activas.stream().noneMatch(Direccion::isPrincipal)) {
            inconsistencias.add("direcciones: ninguna dirección principal");
        }
        return new Evaluacion(List.copyOf(incompleto), List.copyOf(inconsistencias));
    }

    private static EventoClienteRecibido.DatosCliente datos(Cliente cliente,
                                                            List<EventoClienteRecibido.DatosDireccion> direcciones) {
        return new EventoClienteRecibido.DatosCliente(null, null, cliente.getNombres(), cliente.getApellidos(),
                cliente.getTipoDocumento(), cliente.getNumeroDocumento(),
                new EventoClienteRecibido.Contacto(cliente.getEmail(), cliente.getTelefono()),
                direcciones, EventoClienteRecibido.Campos.TODOS);
    }

    public record Evaluacion(List<String> incompleto, List<String> inconsistencias) {
    }
}
