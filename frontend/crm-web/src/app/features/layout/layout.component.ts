import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NombresDeRol, Permisos } from '../../core/auth/sesion';
import { SiTienePermisoDirective } from '../../core/auth/si-tiene-permiso.directive';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SiTienePermisoDirective],
  template: `
    <header>
      <strong>CRM MaxiConecta</strong>
      <nav>
        <a routerLink="/" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
        <a *appSiTienePermiso="permisos.USUARIOS_ADMINISTRAR" routerLink="/admin/usuarios" routerLinkActive="activo">
          Usuarios y roles
        </a>
      </nav>
      <div class="usuario">
        <span>{{ auth.sesion()?.nombreCompleto }} · {{ nombreRol() }}</span>
        <button class="secundario" type="button" (click)="auth.cerrarSesion()">Cerrar sesión</button>
      </div>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    header {
      display: flex; align-items: center; gap: 1.5rem; flex-wrap: wrap;
      padding: 0.75rem 1.5rem; background: #fff; border-bottom: 1px solid var(--color-borde);
    }
    header strong { color: var(--color-primario); }
    nav { display: flex; gap: 1rem; flex: 1; }
    nav a { color: var(--color-texto-suave); text-decoration: none; }
    nav a.activo { color: var(--color-primario); font-weight: 600; }
    .usuario { display: flex; align-items: center; gap: 0.75rem; color: var(--color-texto-suave); }
    main { padding: 1.5rem; max-width: 1100px; margin: 0 auto; }
  `,
})
export class LayoutComponent {
  readonly auth = inject(AuthService);
  readonly permisos = Permisos;
  readonly nombreRol = computed(() => {
    const rol = this.auth.sesion()?.rol ?? '';
    return NombresDeRol[rol] ?? rol;
  });
}
