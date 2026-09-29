import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permisos } from '../../core/auth/sesion';
import { IconoComponent } from '../../core/ui/icono.component';
import { Bitacora, IngestaService } from '../ingesta/ingesta.service';

interface Modulo {
  titulo: string;
  descripcion: string;
  permiso: string;
  icono: string;
  ruta?: string;
}

/** Módulos del CRM. Cada usuario ve solo los que su rol permite; los que no tienen ruta llegan en próximos sprints. */
const MODULOS: Modulo[] = [
  {
    titulo: 'Clientes',
    descripcion: 'Perfil único, historial de cambios, compras y consentimiento de cada cliente.',
    permiso: Permisos.CLIENTE_CONSULTAR,
    icono: 'clientes',
    ruta: '/clientes',
  },
  {
    titulo: 'Revisión de perfiles',
    descripcion: 'Perfiles incompletos o inconsistentes, con el motivo de cada uno.',
    permiso: Permisos.CLIENTE_CONSULTAR,
    icono: 'revision',
    ruta: '/perfiles/revision',
  },
  {
    titulo: 'Eventos de venta',
    descripcion: 'Bitácora de lo que llega de Marketplace y Ventas, y reproceso de los fallidos.',
    permiso: Permisos.EVENTOS_REPROCESAR,
    icono: 'eventos',
    ruta: '/eventos',
  },
  {
    titulo: 'Usuarios',
    descripcion: 'Usuarios internos y asignación de roles.',
    permiso: Permisos.USUARIOS_ADMINISTRAR,
    icono: 'usuarios',
    ruta: '/admin/usuarios',
  },
  {
    titulo: 'Roles y permisos',
    descripcion: 'Roles del CRM y los permisos de cada uno.',
    permiso: Permisos.USUARIOS_ADMINISTRAR,
    icono: 'roles',
    ruta: '/admin/roles',
  },
  {
    titulo: 'Auditoría',
    descripcion: 'Quién consultó o modificó datos de un cliente, y cuándo.',
    permiso: Permisos.AUDITORIA_CONSULTAR,
    icono: 'auditoria',
    ruta: '/admin/auditoria',
  },
  {
    titulo: 'Segmentación',
    descripcion: 'Segmentos de clientes según su comportamiento.',
    permiso: Permisos.SEGMENTOS_CONSULTAR,
    icono: 'segmentos',
  },
  {
    titulo: 'Fidelización',
    descripcion: 'Saldo, nivel y canje de puntos.',
    permiso: Permisos.PUNTOS_CONSULTAR,
    icono: 'fidelizacion',
  },
  {
    titulo: 'Interacciones',
    descripcion: 'Atención y seguimiento de cada cliente.',
    permiso: Permisos.INTERACCIONES_CONSULTAR,
    icono: 'interacciones',
  },
];

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [RouterLink, IconoComponent],
  template: `
    <section class="bienvenida">
      <div>
        <p class="saludo">{{ saludo() }}</p>
        <h1>{{ auth.sesion()?.nombreCompleto }}</h1>
        <p class="subtitulo">Estos son los módulos disponibles para su rol.</p>
      </div>
    </section>

    @if (puedeVerEventos()) {
      <section class="resumen">
        <header>
          <h2>Eventos de Marketplace y Ventas</h2>
          <a routerLink="/eventos">Ver bitácora <app-icono nombre="flecha" [tamanio]="16" /></a>
        </header>
        @if (bitacora(); as datos) {
          <div class="indicadores">
            <article class="indicador">
              <span class="valor">{{ datos.total }}</span>
              <span class="nombre">Total de eventos</span>
            </article>
            <article class="indicador exito">
              <span class="valor">{{ datos.resumen.PROCESADO }}</span>
              <span class="nombre">Procesados</span>
            </article>
            <article class="indicador aviso">
              <span class="valor">{{ datos.resumen.DESCARTADO }}</span>
              <span class="nombre">Descartados por duplicado</span>
            </article>
            <article class="indicador error">
              <span class="valor">{{ datos.resumen.FALLIDO }}</span>
              <span class="nombre">Fallidos</span>
            </article>
          </div>
          <p class="periodo">Últimos 7 días</p>
        } @else if (errorResumen()) {
          <p class="sin-datos">No se pudo obtener el resumen de eventos.</p>
        } @else {
          <p class="sin-datos">Cargando resumen…</p>
        }
      </section>
    }

    <section>
      <h2 class="titulo-seccion">Módulos</h2>
      <div class="modulos">
        @for (modulo of disponibles(); track modulo.titulo) {
          <a class="modulo tarjeta" [routerLink]="modulo.ruta">
            <span class="icono"><app-icono [nombre]="modulo.icono" [tamanio]="22" /></span>
            <span class="texto">
              <strong>{{ modulo.titulo }}</strong>
              <span>{{ modulo.descripcion }}</span>
            </span>
            <app-icono class="ir" nombre="flecha" [tamanio]="18" />
          </a>
        }
      </div>
    </section>

    @if (proximos().length > 0) {
      <section>
        <h2 class="titulo-seccion">Próximos sprints</h2>
        <div class="modulos">
          @for (modulo of proximos(); track modulo.titulo) {
            <div class="modulo tarjeta proximo">
              <span class="icono"><app-icono [nombre]="modulo.icono" [tamanio]="22" /></span>
              <span class="texto">
                <strong>{{ modulo.titulo }}</strong>
                <span>{{ modulo.descripcion }}</span>
              </span>
              <span class="etiqueta">Próximamente</span>
            </div>
          }
        </div>
      </section>
    }
  `,
  styles: `
    .bienvenida { margin-bottom: 1.75rem; }
    .saludo { margin: 0; color: var(--color-primario); font-weight: 600; font-size: 0.9rem; }
    .subtitulo { margin-bottom: 0; }

    .resumen {
      margin-bottom: 2rem; padding: 1.25rem 1.5rem; border-radius: var(--radio);
      background: var(--color-superficie); border: 1px solid var(--color-borde); box-shadow: var(--sombra-1);
    }
    .resumen header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
    .resumen h2 { margin: 0; }
    .resumen header a { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.88rem; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 0.85rem; }
    @media (max-width: 520px) { .indicadores { grid-template-columns: repeat(2, 1fr); } .valor { font-size: 1.45rem; } }
    .indicador {
      display: flex; flex-direction: column; gap: 0.15rem; padding: 0.9rem 1rem; border-radius: 8px;
      background: var(--color-superficie-suave); border: 1px solid var(--color-borde);
      border-left: 4px solid var(--color-primario);
    }
    .indicador.exito { border-left-color: var(--color-exito); }
    .indicador.aviso { border-left-color: var(--color-aviso); }
    .indicador.error { border-left-color: var(--color-error); }
    .valor { font-size: 1.75rem; font-weight: 700; letter-spacing: -0.02em; }
    .nombre { color: var(--color-texto-suave); font-size: 0.85rem; }
    .periodo, .sin-datos { margin: 0.75rem 0 0; color: var(--color-texto-suave); font-size: 0.82rem; }

    .titulo-seccion { margin: 0 0 0.85rem; }
    section + section { margin-top: 2rem; }
    .modulos { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 0.9rem; }
    .modulo {
      display: flex; align-items: flex-start; gap: 0.9rem; padding: 1.1rem 1.2rem; color: var(--color-texto);
      transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
    }
    a.modulo:hover { text-decoration: none; border-color: #b9c6ef; box-shadow: var(--sombra-2); transform: translateY(-1px); }
    .icono {
      display: grid; place-items: center; width: 42px; height: 42px; border-radius: 10px; flex-shrink: 0;
      background: var(--color-primario-claro); color: var(--color-primario);
    }
    .texto { display: flex; flex-direction: column; gap: 0.2rem; flex: 1; }
    .texto strong { font-size: 0.98rem; }
    .texto span { color: var(--color-texto-suave); font-size: 0.86rem; font-weight: 400; line-height: 1.4; }
    .ir { color: var(--color-borde-fuerte); margin-top: 0.6rem; }
    a.modulo:hover .ir { color: var(--color-primario); }
    .proximo { opacity: 0.75; }
    .proximo .icono { background: #eef1f7; color: var(--color-texto-suave); }
  `,
})
export class InicioComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly ingesta = inject(IngestaService);

  readonly bitacora = signal<Bitacora | null>(null);
  readonly errorResumen = signal(false);

  private readonly permisos = computed(() => this.auth.sesion()?.permisos ?? []);
  readonly puedeVerEventos = computed(() => this.permisos().includes(Permisos.EVENTOS_REPROCESAR));
  readonly disponibles = computed(() => MODULOS.filter((m) => m.ruta && this.permisos().includes(m.permiso)));
  readonly proximos = computed(() => MODULOS.filter((m) => !m.ruta && this.permisos().includes(m.permiso)));

  readonly saludo = computed(() => {
    const hora = new Date().getHours();
    return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  ngOnInit(): void {
    if (!this.puedeVerEventos()) {
      return;
    }
    // Sin fechas, el servidor devuelve los últimos 7 días.
    this.ingesta.buscar({ desde: '', hasta: '', estado: '', transaccion: '' }, 0, 1).subscribe({
      next: (datos) => this.bitacora.set(datos),
      error: () => this.errorResumen.set(true),
    });
  }
}
