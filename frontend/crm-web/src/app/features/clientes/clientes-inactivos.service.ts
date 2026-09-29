import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ClienteInactivo {
  idClienteOrigen: string;
  ultimaCompra: string;
  diasTranscurridos: number;
}

export interface PaginaClientesInactivos {
  umbralDias: number;
  content: ClienteInactivo[];
  pagina: number;
  tamanio: number;
  total: number;
}

/**
 * Clientes inactivos (SCRUM-31, GET /api/comportamiento/clientes?estado=inactivo): sin compras
 * vigentes desde hace más del umbral configurado (docs/compra/clientes-inactivos.md).
 */
@Injectable({ providedIn: 'root' })
export class ClientesInactivosService {
  private readonly http = inject(HttpClient);

  buscar(pagina: number, tamanio: number): Observable<PaginaClientesInactivos> {
    const params = new HttpParams().set('estado', 'inactivo').set('pagina', pagina).set('tamanio', tamanio);
    return this.http.get<PaginaClientesInactivos>('/api/comportamiento/clientes', { params });
  }
}
