import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Sesion } from './sesion';

const CLAVE_SESION = 'crm.sesion';

/**
 * Inicio y cierre de sesión. La sesión se guarda en sessionStorage:
 * se pierde al cerrar la pestaña y deja de ser válida al vencer el token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesionActual = signal<Sesion | null>(leerSesionGuardada());

  /** Sesión actual, o null si no hay una vigente. */
  readonly sesion = this.sesionActual.asReadonly();

  iniciarSesion(usuario: string, password: string): Observable<Sesion> {
    return this.http.post<Sesion>('/api/auth/login', { usuario, password }).pipe(
      tap((sesion) => {
        sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
        this.sesionActual.set(sesion);
      }),
    );
  }

  cerrarSesion(): void {
    sessionStorage.removeItem(CLAVE_SESION);
    this.sesionActual.set(null);
    this.router.navigate(['/login']);
  }

  estaAutenticado(): boolean {
    return this.sesionVigente() !== null;
  }

  tienePermiso(permiso: string): boolean {
    return this.sesionVigente()?.permisos.includes(permiso) ?? false;
  }

  token(): string | null {
    return this.sesionVigente()?.token ?? null;
  }

  private sesionVigente(): Sesion | null {
    const sesion = this.sesionActual();
    if (!sesion || new Date(sesion.expiraEn).getTime() <= Date.now()) {
      return null;
    }
    return sesion;
  }
}

function leerSesionGuardada(): Sesion | null {
  try {
    const guardada = sessionStorage.getItem(CLAVE_SESION);
    return guardada ? (JSON.parse(guardada) as Sesion) : null;
  } catch {
    return null;
  }
}
