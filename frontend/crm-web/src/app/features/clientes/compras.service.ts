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
  origen: 'MARKETPLACE' | 'VENTAS';
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

export interface IndicadoresCliente {
  ticketPromedio: TicketPromedio;
}

/** Consumo vigente del cliente en una categoría (docs/compra/categorias-mas-consumidas.md). */
export interface CategoriaConsumida {
  categoria: string;
  compras: number;
  unidades: number;
  monto: number;
  /** Verdadero para el grupo «Sin categoría»: compras cuya categoría hay que corregir. */
  sinCategoria: boolean;
}

export interface CategoriasConsumidas {
  categorias: CategoriaConsumida[];
}

/**
 * Historial de compras (SCRUM-16, GET /api/comportamiento/clientes/{clienteId}/compras) e
 * indicadores (SCRUM-17, .../indicadores) y categorías más consumidas (SCRUM-18, .../categorias) de un cliente.
 */
@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);

  /** Categorías más consumidas (SCRUM-18), de mayor a menor consumo. */
  categorias(idCliente: number, identificadores: IdentificadorOrigen[]): Observable<CategoriasConsumidas> {
    return this.http.get<CategoriasConsumidas>(`/api/comportamiento/clientes/${idCliente}/categorias`, {
      params: parametrosDeIdentificadores(new HttpParams(), identificadores),
    });
  }

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
  return identificadores.reduce((acumulado, id) => acumulado.append('identificador', `${id.origen}:${id.idCliente}`), params);
}
