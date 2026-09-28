import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface UsuarioInterno {
  id: number;
  nombreUsuario: string;
  nombreCompleto: string;
  rol: string;
  activo: boolean;
}

export interface Rol {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  permisos: string[];
}

export interface PermisoDisponible {
  codigo: string;
  descripcion: string;
}

export interface DatosRol {
  nombre: string;
  descripcion: string | null;
  permisos: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  listarUsuarios(): Observable<UsuarioInterno[]> {
    return this.http.get<UsuarioInterno[]>('/api/admin/usuarios');
  }

  listarRoles(): Observable<Rol[]> {
    return this.http.get<Rol[]>('/api/admin/roles');
  }

  listarPermisos(): Observable<PermisoDisponible[]> {
    return this.http.get<PermisoDisponible[]>('/api/admin/permisos');
  }

  crearRol(codigo: string, datos: DatosRol): Observable<Rol> {
    return this.http.post<Rol>('/api/admin/roles', { codigo, ...datos });
  }

  actualizarRol(idRol: number, datos: DatosRol): Observable<Rol> {
    return this.http.put<Rol>(`/api/admin/roles/${idRol}`, datos);
  }

  desactivarRol(idRol: number): Observable<Rol> {
    return this.http.patch<Rol>(`/api/admin/roles/${idRol}/desactivar`, {});
  }

  asignarRol(idUsuario: number, rol: string): Observable<UsuarioInterno> {
    return this.http.put<UsuarioInterno>(`/api/admin/usuarios/${idUsuario}/rol`, { rol });
  }

  desactivarUsuario(idUsuario: number): Observable<UsuarioInterno> {
    return this.http.patch<UsuarioInterno>(`/api/admin/usuarios/${idUsuario}/desactivar`, {});
  }
}
