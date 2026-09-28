package com.maxiconecta.crm.comportamiento.ingesta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Flujo de ingesta de un mensaje de compra confirmada: primero se anota en la bitácora y
 * después se procesa en una transacción aparte.
 */
@Service
public class IngestaCompras {

    private static final Logger log = LoggerFactory.getLogger(IngestaCompras.class);

    private final BitacoraIngesta bitacora;
    private final ProcesadorCompras procesador;

    public IngestaCompras(BitacoraIngesta bitacora, ProcesadorCompras procesador) {
        this.bitacora = bitacora;
        this.procesador = procesador;
    }

    public void recibir(String contenido) {
        Long idEvento = bitacora.registrarRecepcion(contenido);
        try {
            procesador.procesar(idEvento);
        } catch (CompraDuplicadaException ex) {
            log.info("Evento {} descartado: {}", idEvento, ex.getMessage());
            bitacora.marcarDescartado(idEvento, ex.getMessage());
        }
    }
}
