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

@Injectable({ providedIn: 'root' })
export class PerfilesService {
  private readonly http = inject(HttpClient);

  /** Perfiles en un estado dado (SCRUM-12): INCOMPLETO o INCONSISTENTE para la revisión. */
  buscarPorEstado(estado: EstadoPerfil, pagina: number, tamanio: number): Observable<PaginaPerfiles> {
    const params = new HttpParams().set('estado', estado).set('pagina', pagina).set('tamanio', tamanio);
    return this.http.get<PaginaPerfiles>('/api/perfil/clientes', { params });
  }
}
