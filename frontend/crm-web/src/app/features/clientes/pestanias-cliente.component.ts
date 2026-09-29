import { Component, input } from '@angular/core';
import { SiTienePermisoDirective } from '../../core/auth/si-tiene-permiso.directive';
import { Permisos } from '../../core/auth/sesion';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Navegación entre las vistas de un mismo cliente, con el regreso a la lista. */
@Component({
  selector: 'app-pestanias-cliente',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, SiTienePermisoDirective],
  template: `
    <nav class="navegacion" aria-label="Vistas del cliente">
      <a class="volver" routerLink="/clientes">← Clientes</a>
      <div class="pestanias" role="tablist">
        <a *appSiTienePermiso="permisos.FICHA_INTEGRAL_CONSULTAR" [routerLink]="['/clientes', id(), 'ficha-integral']" routerLinkActive="activa" role="tab">Ficha integral</a>
        <a [routerLink]="['/clientes', id(), 'historial']" routerLinkActive="activa" role="tab">Historial de cambios</a>
        <a [routerLink]="['/clientes', id(), 'compras']" routerLinkActive="activa" role="tab">Compras e indicadores</a>
        <a [routerLink]="['/clientes', id(), 'consentimiento']" routerLinkActive="activa" role="tab">Consentimiento</a>
      </div>
    </nav>
  `,
  styles: `
    .navegacion {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap;
      margin-bottom: 1.25rem;
    }
    .volver { font-size: 0.9rem; }
    .pestanias {
      display: inline-flex; gap: 0.25rem; padding: 0.25rem; border-radius: 10px;
      background: #e9edf6; border: 1px solid var(--color-borde);
    }
    .pestanias a {
      padding: 0.4rem 0.85rem; border-radius: 7px; font-size: 0.86rem; font-weight: 600;
      color: var(--color-texto-suave);
    }
    .pestanias a:hover { text-decoration: none; color: var(--color-texto); }
    .pestanias a.activa { background: var(--color-superficie); color: var(--color-primario); box-shadow: var(--sombra-1); }
  `,
})
export class PestaniasClienteComponent {
  /** Identificador del cliente en el CRM. */
  readonly id = input.required<string | number>();

  readonly permisos = Permisos;
}
