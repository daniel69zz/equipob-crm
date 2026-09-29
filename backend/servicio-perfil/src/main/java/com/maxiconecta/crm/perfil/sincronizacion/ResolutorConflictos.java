package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.CampoOrigen;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.configuracion.PoliticaConflictos;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRecibido.Campos;

/**
 * Resuelve los conflictos entre Marketplace y Ventas: un campo que cambia a un valor distinto del
 * actual cuando el valor actual lo puso otro sistema. Identificación: prioridad por sistema.
 * Contacto: gana el cambio más reciente. Las direcciones no tienen conflicto (cada sistema las suyas).
 */
@Component
public class ResolutorConflictos {

    static final Set<String> IDENTIFICACION = Set.of(Campos.NOMBRES, Campos.APELLIDOS, Campos.TIPO_DOCUMENTO,
            Campos.NUMERO_DOCUMENTO);
    static final Set<String> CONTACTO = Set.of(Campos.EMAIL, Campos.TELEFONO);
    static final List<String> CAMPOS = List.of(Campos.NOMBRES, Campos.APELLIDOS, Campos.TIPO_DOCUMENTO,
            Campos.NUMERO_DOCUMENTO, Campos.EMAIL, Campos.TELEFONO);

    private final PoliticaConflictos politica;

    public ResolutorConflictos(PoliticaConflictos politica) {
        this.politica = politica;
    }

    /**
     * @param procedencia qué sistema puso cada campo actual, por nombre de campo
     * @return la propuesta con los campos conservados donde el conflicto lo decidió, y los conflictos
     */
    public Resolucion resolver(Cliente actual, EventoClienteRecibido.DatosCliente propuesta, Origen origen,
                               OffsetDateTime fechaCambio, Map<String, CampoOrigen> procedencia, Long idEvento) {
        List<ConflictoPerfil> conflictos = new ArrayList<>();
        EventoClienteRecibido.DatosCliente resultado = propuesta;
        for (String campo : CAMPOS) {
            String valorActual = valor(actual, campo);
            String valorRecibido = valor(propuesta, campo);
            CampoOrigen puestoPor = procedencia.get(campo);
            if (!propuesta.informa(campo) || valorActual == null || Objects.equals(valorActual, valorRecibido)
                    || puestoPor == null || puestoPor.getOrigen() == origen || !puestoPor.getOrigen().esSistemaExterno()) {
                continue;
            }
            ConflictoPerfil.Regla regla;
            boolean aplicar;
            if (IDENTIFICACION.contains(campo)) {
                regla = ConflictoPerfil.Regla.PRIORIDAD_SISTEMA;
                aplicar = politica.prioridad(origen) < politica.prioridad(puestoPor.getOrigen());
            } else {
                regla = ConflictoPerfil.Regla.MAS_RECIENTE;
                aplicar = fechaCambio.isAfter(puestoPor.getActualizadoEn());
            }
            ConflictoPerfil.Decision decision = aplicar ? ConflictoPerfil.Decision.APLICADO
                    : ConflictoPerfil.Decision.CONSERVADO;
            conflictos.add(new ConflictoPerfil(actual.getId(), campo, valorActual, puestoPor.getOrigen(), valorRecibido,
                    origen, decision, regla, idEvento));
            if (!aplicar) {
                resultado = conValor(resultado, campo, valorActual);
            }
        }
        return new Resolucion(resultado, conflictos);
    }

    static String valor(Cliente cliente, String campo) {
        return switch (campo) {
            case Campos.NOMBRES -> cliente.getNombres();
            case Campos.APELLIDOS -> cliente.getApellidos();
            case Campos.TIPO_DOCUMENTO -> cliente.getTipoDocumento();
            case Campos.NUMERO_DOCUMENTO -> cliente.getNumeroDocumento();
            case Campos.EMAIL -> cliente.getEmail();
            case Campos.TELEFONO -> cliente.getTelefono();
            default -> throw new IllegalArgumentException("Campo sin conflicto: " + campo);
        };
    }

    private static String valor(EventoClienteRecibido.DatosCliente datos, String campo) {
        return switch (campo) {
            case Campos.NOMBRES -> datos.nombres();
            case Campos.APELLIDOS -> datos.apellidos();
            case Campos.TIPO_DOCUMENTO -> datos.tipoDocumento();
            case Campos.NUMERO_DOCUMENTO -> datos.numeroDocumento();
            case Campos.EMAIL -> datos.contacto().email();
            case Campos.TELEFONO -> datos.contacto().telefono();
            default -> throw new IllegalArgumentException("Campo sin conflicto: " + campo);
        };
    }

    private static EventoClienteRecibido.DatosCliente conValor(EventoClienteRecibido.DatosCliente d, String campo,
                                                               String valor) {
        EventoClienteRecibido.Contacto c = d.contacto();
        return new EventoClienteRecibido.DatosCliente(d.idCliente(), d.fechaActualizacion(),
                campo.equals(Campos.NOMBRES) ? valor : d.nombres(),
                campo.equals(Campos.APELLIDOS) ? valor : d.apellidos(),
                campo.equals(Campos.TIPO_DOCUMENTO) ? valor : d.tipoDocumento(),
                campo.equals(Campos.NUMERO_DOCUMENTO) ? valor : d.numeroDocumento(),
                new EventoClienteRecibido.Contacto(campo.equals(Campos.EMAIL) ? valor : c.email(),
                        campo.equals(Campos.TELEFONO) ? valor : c.telefono()),
                d.direcciones(), d.camposInformados());
    }

    public record Resolucion(EventoClienteRecibido.DatosCliente propuesta, List<ConflictoPerfil> conflictos) {

        public String resumen() {
            return conflictos.isEmpty() ? null : conflictos.size() + " conflicto(s) resuelto(s): "
                    + String.join("; ", conflictos.stream().map(ConflictoPerfil::resumen).toList());
        }
    }
}
