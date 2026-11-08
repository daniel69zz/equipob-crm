import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type TipoMovimientoPuntos = 'ACUMULACION' | 'REDENCION' | 'REVERSA';

/** Un movimiento reciente de puntos (docs/fidelizacion/consulta-saldo-puntos.md). */
export interface MovimientoPuntos {
  idTransaccion: string;
  tipoMovimiento: TipoMovimientoPuntos;
  puntos: number;
  /** Lo que el movimiento sumó (positivo) o restó (negativo) del saldo. */
  variacionSaldo: number;
  saldoResultante: number;
  /** En una reversa, la compra anulada o devuelta. */
  idTransaccionRevertida: string | null;
  fechaTransaccion: string;
  registradoEn: string;
}

/** Saldo, nivel y últimos movimientos de puntos de un cliente (SCRUM-47). */
export interface SaldoPuntosCliente {
  clienteId: number;
  tienePuntos: boolean;
  /** Solo cuando el cliente aún no tiene puntos (criterio de aceptación 2). */
  mensaje: string | null;
  saldo: number;
  nivel: string | null;
  puntosPeriodo: number;
  actualizadoEn: string | null;
  nivelActualizadoEn: string | null;
  totalMovimientos: number;
  ultimosMovimientos: MovimientoPuntos[];
}

@Injectable({ providedIn: 'root' })
export class SaldoPuntosService {
  private readonly http = inject(HttpClient);

  consultar(clienteId: number, movimientos = 10) {
    return this.http.get<SaldoPuntosCliente>(`/api/fidelizacion/clientes/${clienteId}/saldo`, {
      params: new HttpParams().set('movimientos', movimientos),
    });
  }
}
