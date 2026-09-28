import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface EventoAuditoria {
  id: number;
  ocurridoEn: string;
  usuario: string;
  operacion: string;
  entidad: string;
  entidadId: string | null;
  detalle: string | null;
}

export interface PaginaEventos {
  eventos: EventoAuditoria[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Filtros de la consulta. Las fechas van en formato AAAA-MM-DD. */
export interface FiltroAuditoria {
  usuario: string;
  operacion: string;
  clienteId: string;
  desde: string;
  hasta: string;
}

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);

  buscar(filtro: FiltroAuditoria, pagina: number, tamanio: number): Observable<PaginaEventos> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtro)) {
      if (valor.trim()) {
        params = params.set(clave, valor.trim());
      }
    }
    return this.http.get<PaginaEventos>('/api/admin/auditoria', { params });
  }

  listarOperaciones(): Observable<string[]> {
    return this.http.get<string[]>('/api/admin/auditoria/operaciones');
  }
}
