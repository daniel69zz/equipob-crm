import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IdentificadorOrigen } from './clientes.service';

export interface ItemCompra {
  categoria: string;
  cantidad: number;
  monto: number;
}

export interface Compra {
  fecha: string;
  referencia: string;
  montoTotal: number;
  estado: 'CONFIRMADA' | 'DEVOLUCION_PARCIAL' | 'ANULADA';
  items: ItemCompra[];
}

export interface PaginaCompras {
  content: Compra[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Sin compras vigentes `valor` es nulo y `sinDatos` verdadero (docs/compra/ticket-promedio.md). */
export interface TicketPromedio {
  valor: number | null;
  compras: number;
  montoAcumulado: number;
  sinDatos: boolean;
}

/** SCRUM-19 · Duración exacta ISO-8601 desde la última compra vigente. */
export interface RecenciaCompra {
  ultimaCompra: string | null;
  tiempoTranscurrido: string | null;
  sinDatos: boolean;
}

/** Sin compras vigentes `valor` es 0 (una suma sí está definida) y `sinDatos` verdadero (docs/compra/valor-acumulado.md). */
export interface ValorAcumulado {
  valor: number;
  compras: number;
  sinDatos: boolean;
}

export interface IndicadoresCliente {
  ticketPromedio: TicketPromedio;
  recencia: RecenciaCompra;
  frecuencia: number;
  valorAcumulado: ValorAcumulado;
}

/**
 * Historial de compras (SCRUM-16, GET /api/comportamiento/clientes/{clienteId}/compras) e
 * indicadores (SCRUM-17, SCRUM-19 y SCRUM-37, .../indicadores) de un cliente.
 */
@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);

  indicadores(idCliente: number, identificadores: IdentificadorOrigen[]): Observable<IndicadoresCliente> {
    return this.http.get<IndicadoresCliente>(`/api/comportamiento/clientes/${idCliente}/indicadores`, {
      params: parametrosDeIdentificadores(new HttpParams(), identificadores),
    });
  }

  historial(
    idCliente: number,
    identificadores: IdentificadorOrigen[],
    pagina: number,
    tamanio: number,
  ): Observable<PaginaCompras> {
    const params = parametrosDeIdentificadores(
      new HttpParams().set('pagina', pagina).set('tamanio', tamanio),
      identificadores,
    );
    return this.http.get<PaginaCompras>(`/api/comportamiento/clientes/${idCliente}/compras`, { params });
  }
}

function parametrosDeIdentificadores(params: HttpParams, identificadores: IdentificadorOrigen[]): HttpParams {
  return identificadores.reduce((acumulado, id) => acumulado.append('identificador', id.idCliente), params);
}
