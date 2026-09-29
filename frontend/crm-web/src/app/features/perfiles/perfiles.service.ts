import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EstadoPerfil = 'COMPLETO' | 'INCOMPLETO' | 'INCONSISTENTE';

export interface ResumenPerfil {
  id: number;
  nombres: string | null;
  apellidos: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  estado: EstadoPerfil;
  motivosIncidencia: string | null;
  actualizadoEn: string;
}

export interface PaginaPerfiles {
  clientes: ResumenPerfil[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Filtros de la consulta (SCRUM-167). Las fechas van en formato AAAA-MM-DD. */
export interface FiltroPerfiles {
  tipoDocumento: string;
  numeroDocumento: string;
  motivo: string;
  desde: string;
  hasta: string;
}

@Injectable({ providedIn: 'root' })
export class PerfilesService {
  private readonly http = inject(HttpClient);

  /** Perfiles en un estado dado (SCRUM-12): INCOMPLETO o INCONSISTENTE para la revisión. */
  buscar(estado: EstadoPerfil, filtro: FiltroPerfiles, pagina: number, tamanio: number): Observable<PaginaPerfiles> {
    let params = new HttpParams().set('estado', estado).set('pagina', pagina).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtro)) {
      if (valor.trim()) {
        params = params.set(clave, valor.trim());
      }
    }
    return this.http.get<PaginaPerfiles>('/api/perfil/clientes', { params });
  }
}
