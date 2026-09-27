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

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  listarUsuarios(): Observable<UsuarioInterno[]> {
    return this.http.get<UsuarioInterno[]>('/api/admin/usuarios');
  }

  listarRoles(): Observable<Rol[]> {
    return this.http.get<Rol[]>('/api/admin/roles');
  }

  asignarRol(idUsuario: number, rol: string): Observable<UsuarioInterno> {
    return this.http.put<UsuarioInterno>(`/api/admin/usuarios/${idUsuario}/rol`, { rol });
  }

  desactivarUsuario(idUsuario: number): Observable<UsuarioInterno> {
    return this.http.patch<UsuarioInterno>(`/api/admin/usuarios/${idUsuario}/desactivar`, {});
  }
}
