package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.consulta.ListadoClientesRepository.FilaCliente;
import com.maxiconecta.crm.segmentacion.consulta.PerfilesClientes.Nombre;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Página del listado de clientes por segmento o nivel de consumo (SCRUM-277). {@code orden} y
 * {@code direccion} son los que se aplicaron, para que la pantalla marque la columna.
 * {@code nombresDisponibles} es falso cuando servicio-perfil no respondió: los clientes llegan sin
 * nombre.
 */
public record ListadoClientes(List<Cliente> content, int pagina, int tamanio, long total, String orden,
                              String direccion, boolean nombresDisponibles) {

    public record Cliente(long clienteId, String nombres, String apellidos, String segmento, String indicador,
                          OffsetDateTime asignadoEn, String nivelConsumo, BigDecimal valorAcumulado, Long frecuencia,
                          OffsetDateTime ultimaCompra) {

        static Cliente de(FilaCliente fila, Nombre nombre) {
            return new Cliente(fila.clienteId(), nombre.nombres(), nombre.apellidos(), fila.segmento(),
                    fila.indicador(), fila.asignadoEn(), fila.nivelConsumo(), fila.valor(), fila.frecuencia(),
                    fila.ultimaCompra());
        }
    }
}
