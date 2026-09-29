import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** De dónde vino un cambio: del módulo Marketplace y Ventas o de una acción en el CRM. */
export type OrigenCambio = 'MARKETPLACE_VENTAS' | 'CRM';

export interface ResumenCliente {
  id: number;
  nombres: string | null;
  apellidos: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  estado: 'COMPLETO' | 'INCOMPLETO' | 'INCONSISTENTE';
  actualizadoEn: string;
}

export interface PaginaClientes {
  clientes: ResumenCliente[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Identificador del cliente en Marketplace y Ventas vinculado al perfil. */
export interface IdentificadorOrigen {
  idCliente: string;
}

export interface PerfilCliente extends ResumenCliente {
  email: string | null;
  telefono: string | null;
  motivosIncompleto: string | null;
  idClienteConsolidado: number | null;
  identificadoresOrigen: IdentificadorOrigen[];
}

export interface CampoCambiado {
  campo: string;
  anterior: string | null;
  nuevo: string | null;
}

export interface CambioHistorial {
  id: number;
  fecha: string;
  tipo: 'CREACION' | 'ACTUALIZACION' | 'VINCULACION' | 'UNIFICACION';
  origen: OrigenCambio;
  responsable: string;
  idEvento: number | null;
  campos: CampoCambiado[];
}

export interface PaginaHistorial {
  cambios: CambioHistorial[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Filtros como texto: los vacíos no se envían. */
export type Filtro = Record<string, string>;

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);

  buscar(filtro: Filtro): Observable<PaginaClientes> {
    return this.http.get<PaginaClientes>('/api/perfil/clientes', { params: parametros(filtro) });
  }

  obtener(idCliente: number): Observable<PerfilCliente> {
    return this.http.get<PerfilCliente>(`/api/perfil/clientes/${idCliente}`);
  }

  historial(idCliente: number, filtro: Filtro, pagina: number, tamanio: number): Observable<PaginaHistorial> {
    const params = parametros(filtro).set('pagina', pagina).set('tamanio', tamanio);
    return this.http.get<PaginaHistorial>(`/api/perfil/clientes/${idCliente}/historial-cambios`, { params });
  }
}

function parametros(filtro: Filtro): HttpParams {
  let params = new HttpParams();
  for (const [clave, valor] of Object.entries(filtro)) {
    if (valor.trim()) {
      params = params.set(clave, valor.trim());
    }
  }
  return params;
}
