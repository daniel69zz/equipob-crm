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

/** Historial de compras de un cliente (SCRUM-16, GET /api/comportamiento/clientes/{clienteId}/compras). */
@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);

  historial(
    idCliente: number,
    identificadores: IdentificadorOrigen[],
    pagina: number,
    tamanio: number,
  ): Observable<PaginaCompras> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    for (const id of identificadores) {
      params = params.append('identificador', `${id.origen}:${id.idCliente}`);
    }
    return this.http.get<PaginaCompras>(`/api/comportamiento/clientes/${idCliente}/compras`, { params });
  }
}
