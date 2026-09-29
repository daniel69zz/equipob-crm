import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { NombresDeRol, Permisos } from '../../core/auth/sesion';
import { IconoComponent } from '../../core/ui/icono.component';

interface Enlace {
  texto: string;
  ruta: string;
  icono: string;
  permiso?: string;
  exacto?: boolean;
}

interface Grupo {
  titulo: string;
  enlaces: Enlace[];
}

/** Navegación del CRM, agrupada por área. Cada usuario ve solo lo que su rol permite. */
const GRUPOS: Grupo[] = [
  {
    titulo: 'General',
    enlaces: [{ texto: 'Inicio', ruta: '/', icono: 'inicio', exacto: true }],
  },
  {
    titulo: 'Clientes',
    enlaces: [
      { texto: 'Clientes', ruta: '/clientes', icono: 'clientes', permiso: Permisos.CLIENTE_CONSULTAR },
      { texto: 'Revisión de perfiles', ruta: '/perfiles/revision', icono: 'revision', permiso: Permisos.CLIENTE_CONSULTAR },
    ],
  },
  {
    titulo: 'Integración',
    enlaces: [{ texto: 'Eventos de venta', ruta: '/eventos', icono: 'eventos', permiso: Permisos.EVENTOS_REPROCESAR }],
  },
  {
    titulo: 'Administración',
    enlaces: [
      { texto: 'Usuarios', ruta: '/admin/usuarios', icono: 'usuarios', permiso: Permisos.USUARIOS_ADMINISTRAR },
      { texto: 'Roles y permisos', ruta: '/admin/roles', icono: 'roles', permiso: Permisos.USUARIOS_ADMINISTRAR },
      { texto: 'Auditoría', ruta: '/admin/auditoria', icono: 'auditoria', permiso: Permisos.AUDITORIA_CONSULTAR },
    ],
  },
];

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconoComponent],
  template: `
    <div class="aplicacion" [class.menu-abierto]="menuAbierto()">
      <aside class="barra">
        <a class="marca" routerLink="/">
          <span class="logo">MC</span>
          <span class="nombre">
            <strong>MaxiConecta</strong>
            <small>CRM</small>
          </span>
        </a>

        <nav>
          @for (grupo of gruposVisibles(); track grupo.titulo) {
            <p class="grupo">{{ grupo.titulo }}</p>
            @for (enlace of grupo.enlaces; track enlace.ruta) {
              <a
                [routerLink]="enlace.ruta"
                routerLinkActive="activo"
                [routerLinkActiveOptions]="{ exact: !!enlace.exacto }"
              >
                <app-icono [nombre]="enlace.icono" [tamanio]="19" />
                <span>{{ enlace.texto }}</span>
              </a>
            }
          }
        </nav>

        <div class="usuario">
          <span class="avatar">{{ iniciales() }}</span>
          <span class="datos">
            <strong>{{ auth.sesion()?.nombreCompleto }}</strong>
            <small>{{ nombreRol() }}</small>
          </span>
          <button type="button" class="salir" title="Cerrar sesión" aria-label="Cerrar sesión" (click)="auth.cerrarSesion()">
            <app-icono nombre="salir" [tamanio]="18" />
          </button>
        </div>
      </aside>

      <div class="velo" (click)="menuAbierto.set(false)"></div>

      <div class="contenido">
        <header class="superior">
          <button type="button" class="abrir-menu" aria-label="Abrir menú" (click)="menuAbierto.set(true)">
            <app-icono nombre="menu" />
          </button>
          <span class="marca-movil">MaxiConecta CRM</span>
          <span class="integracion" title="El CRM se integra con el módulo Marketplace y Ventas del ERP">
            <span class="punto"></span> Integración: Marketplace y Ventas
          </span>
        </header>
        <main>
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    :host { --ancho-barra: 248px; }
    .aplicacion { display: flex; min-height: 100vh; }

    /* Barra lateral */
    .barra {
      position: fixed; inset: 0 auto 0 0; width: var(--ancho-barra); z-index: 20;
      display: flex; flex-direction: column; gap: 1rem;
      padding: 1.1rem 0.85rem; background: var(--color-barra); color: #c8d0e4;
      transition: transform 0.2s ease;
    }
    .marca { display: flex; align-items: center; gap: 0.7rem; padding: 0.25rem 0.5rem 0.9rem; color: #fff; }
    .marca:hover { text-decoration: none; }
    .logo {
      display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px;
      background: linear-gradient(135deg, var(--color-primario), var(--color-acento));
      font-weight: 800; font-size: 0.85rem; letter-spacing: 0.02em;
    }
    .nombre { display: flex; flex-direction: column; line-height: 1.15; }
    .nombre strong { font-size: 1rem; }
    .nombre small { color: #8793b3; font-size: 0.72rem; font-weight: 600; letter-spacing: 0.12em; }

    nav { flex: 1; display: flex; flex-direction: column; gap: 2px; overflow-y: auto; }
    .grupo {
      margin: 0.9rem 0.6rem 0.35rem; font-size: 0.68rem; font-weight: 700;
      letter-spacing: 0.1em; text-transform: uppercase; color: #6d7899;
    }
    .grupo:first-child { margin-top: 0; }
    nav a {
      display: flex; align-items: center; gap: 0.7rem; padding: 0.55rem 0.65rem;
      border-radius: 8px; color: #c8d0e4; font-weight: 500; font-size: 0.92rem;
    }
    nav a:hover { background: rgba(255, 255, 255, 0.06); color: #fff; text-decoration: none; }
    nav a.activo { background: rgba(94, 129, 244, 0.18); color: #fff; box-shadow: inset 3px 0 0 #6f8ff5; }

    .usuario {
      display: flex; align-items: center; gap: 0.65rem; padding: 0.75rem 0.6rem;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
    }
    .avatar {
      display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%;
      background: #28345a; color: #fff; font-weight: 700; font-size: 0.8rem; flex-shrink: 0;
    }
    .datos { display: flex; flex-direction: column; min-width: 0; flex: 1; line-height: 1.25; }
    .datos strong { color: #fff; font-size: 0.86rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .datos small { color: #8793b3; font-size: 0.75rem; }
    .salir {
      padding: 0.4rem; background: transparent; border: 1px solid rgba(255, 255, 255, 0.12); color: #c8d0e4;
    }
    .salir:hover:not(:disabled) { background: rgba(255, 255, 255, 0.08); border-color: rgba(255, 255, 255, 0.25); color: #fff; }

    /* Contenido */
    .contenido { flex: 1; margin-left: var(--ancho-barra); min-width: 0; display: flex; flex-direction: column; }
    .superior {
      position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 0.75rem;
      height: 56px; padding: 0 2rem; background: rgba(244, 246, 251, 0.85);
      backdrop-filter: blur(8px); border-bottom: 1px solid var(--color-borde);
    }
    .abrir-menu, .marca-movil { display: none; }
    .integracion {
      margin-left: auto; display: inline-flex; align-items: center; gap: 0.45rem;
      font-size: 0.8rem; font-weight: 600; color: var(--color-texto-suave);
      padding: 0.3rem 0.75rem; border-radius: var(--radio-pildora); background: var(--color-superficie);
      border: 1px solid var(--color-borde);
    }
    .punto { width: 8px; height: 8px; border-radius: 50%; background: var(--color-acento); box-shadow: 0 0 0 3px rgba(14, 165, 164, 0.18); }
    main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 1.75rem 2rem 3rem; }
    .velo { display: none; }

    /* Pantallas chicas: la barra se abre como panel */
    @media (max-width: 960px) {
      .barra { transform: translateX(-100%); box-shadow: var(--sombra-2); }
      .menu-abierto .barra { transform: none; }
      .menu-abierto .velo { display: block; position: fixed; inset: 0; z-index: 15; background: rgba(17, 26, 51, 0.45); }
      .contenido { margin-left: 0; }
      .superior { padding: 0 1rem; }
      .abrir-menu {
        display: inline-flex; padding: 0.4rem; background: transparent; color: var(--color-texto);
        border: 1px solid var(--color-borde-fuerte);
      }
      .abrir-menu:hover:not(:disabled) { background: var(--color-superficie); color: var(--color-texto); border-color: var(--color-borde-fuerte); }
      .marca-movil { display: inline; font-weight: 700; }
      .integracion { display: none; }
      main { padding: 1.25rem 1rem 2.5rem; }
    }
  `,
})
export class LayoutComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuAbierto = signal(false);

  readonly nombreRol = computed(() => {
    const rol = this.auth.sesion()?.rol ?? '';
    return NombresDeRol[rol] ?? rol;
  });

  readonly iniciales = computed(() =>
    (this.auth.sesion()?.nombreCompleto ?? '?')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join(''),
  );

  /** Solo los enlaces permitidos; un grupo sin enlaces no se muestra. */
  readonly gruposVisibles = computed(() => {
    const permisos = this.auth.sesion()?.permisos ?? [];
    return GRUPOS.map((grupo) => ({
      ...grupo,
      enlaces: grupo.enlaces.filter((enlace) => !enlace.permiso || permisos.includes(enlace.permiso)),
    })).filter((grupo) => grupo.enlaces.length > 0);
  });

  constructor() {
    // En pantallas chicas, el menú se cierra al navegar.
    this.router.events
      .pipe(filter((evento) => evento instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuAbierto.set(false));
  }

  ngOnInit(): void {
    this.auth.refrescarSesion();
  }
}
