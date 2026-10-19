package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Construcción explícita (SCRUM-41) y actualización con cada compra confirmada o anulación
 * (SCRUM-42, docs/preferencias/actualizacion-por-eventos.md). Ambas recalculan el perfil con todo el
 * historial vigente del cliente, con la misma regla y la misma validación.
 */
@Service
public class PerfilesPreferencias {
    private final IdentificadoresPreferencias identificadores;
    private final AgregadoConsumoCategorias consumo;
    private final PerfilPreferenciasRepository perfiles;
    private final Clock reloj;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public PerfilesPreferencias(IdentificadoresPreferencias identificadores, AgregadoConsumoCategorias consumo,
                               PerfilPreferenciasRepository perfiles, Clock reloj, PlatformTransactionManager manager) {
        this.identificadores = identificadores;
        this.consumo = consumo;
        this.perfiles = perfiles;
        this.reloj = reloj;
        escritura = new TransactionTemplate(manager);
        escritura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lectura = new TransactionTemplate(manager);
        lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lectura.setReadOnly(true);
    }

    public PerfilPreferencias construir(long clienteId) {
        // La red se consulta antes de abrir la transacción. Nunca se aceptan IDs externos del solicitante.
        var ids = identificadores.obtener(clienteId);
        for (int intento = 0; intento < 3; intento++) {
            try {
                return escritura.execute(estado -> {
                    perfiles.bloquear(clienteId);
                    var categorias = ConstructorPreferencias.construir(consumo.consumirTodo(ids));
                    return perfiles.guardar(clienteId, OffsetDateTime.now(reloj), categorias);
                });
            } catch (PessimisticLockingFailureException ex) {
                // PostgreSQL invalida una instantánea si otro constructor guardó mientras se esperaba el bloqueo.
                // Reintentar toda la transacción vuelve a leer las compras y evita sobrescribir datos más recientes.
                if (intento == 2) {
                    throw new PreferenciasException(HttpStatus.CONFLICT,
                            "Otra construcción está actualizando el perfil; intente nuevamente");
                }
            }
        }
        throw new IllegalStateException("No se completó la construcción del perfil");
    }

    /**
     * Recalcula el perfil por un evento de venta ya procesado (SCRUM-366, SCRUM-369). Un evento que ya
     * actualizó el perfil no lo vuelve a actualizar. La actualización y su trazabilidad (fecha, evento,
     * versiones y categorías antes y después) se guardan juntas o no se guarda nada.
     */
    public Actualizacion actualizarPorEvento(long clienteId, String idEvento, String tipoEvento) {
        var ids = identificadores.obtener(clienteId);
        for (int intento = 0; intento < 3; intento++) {
            try {
                return escritura.execute(estado -> {
                    perfiles.bloquear(clienteId);
                    if (perfiles.eventoAplicado(idEvento)) {
                        return new Actualizacion(Resultado.DUPLICADO, perfiles.obtener(clienteId));
                    }
                    PerfilPreferencias anterior = perfiles.obtener(clienteId);
                    var categorias = ConstructorPreferencias.construir(consumo.consumirTodo(ids));
                    PerfilPreferencias nuevo = perfiles.guardar(clienteId, OffsetDateTime.now(reloj), categorias,
                            tipoEvento, idEvento);
                    perfiles.registrarActualizacion(idEvento, tipoEvento, anterior, nuevo);
                    return new Actualizacion(Resultado.ACTUALIZADO, nuevo);
                });
            } catch (DuplicateKeyException ex) {
                // Otra copia del mismo evento terminó primero mientras esta esperaba el bloqueo.
                return new Actualizacion(Resultado.DUPLICADO, lectura.execute(estado -> perfiles.obtener(clienteId)));
            } catch (PessimisticLockingFailureException ex) {
                // Igual que en la construcción: se reintenta con una instantánea nueva.
                if (intento == 2) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("No se completó la actualización del perfil");
    }

    public List<PerfilPreferenciasRepository.Actualizacion> actualizaciones(long clienteId) {
        identificadores.obtener(clienteId);
        return lectura.execute(estado -> perfiles.actualizaciones(clienteId));
    }

    public PerfilPreferencias consultar(long clienteId) {
        identificadores.obtener(clienteId);
        return lectura.execute(estado -> perfiles.obtener(clienteId));
    }

    public record Actualizacion(Resultado resultado, PerfilPreferencias perfil) { }

    public enum Resultado { ACTUALIZADO, DUPLICADO }
}
