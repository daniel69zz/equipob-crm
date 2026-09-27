import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permisos } from '../../core/auth/sesion';

interface Modulo {
  titulo: string;
  descripcion: string;
  permiso: string;
  ruta?: string;
}

/** Módulos del CRM. Cada usuario ve solo los que su rol permite. */
const MODULOS: Modulo[] = [
  { titulo: 'Clientes', descripcion: 'Ficha integral, perfil e historial de cambios', permiso: Permisos.CLIENTE_CONSULTAR },
  { titulo: 'Compras e indicadores', descripcion: 'Historial de compras y comportamiento', permiso: Permisos.INDICADORES_CONSULTAR },
  { titulo: 'Eventos de venta', descripcion: 'Bitácora de ingesta y reproceso de fallidos', permiso: Permisos.EVENTOS_REPROCESAR },
  { titulo: 'Segmentación', descripcion: 'Segmentos y clientes por segmento', permiso: Permisos.SEGMENTOS_CONSULTAR },
  { titulo: 'Fidelización', descripcion: 'Saldo y nivel de puntos', permiso: Permisos.PUNTOS_CONSULTAR },
  { titulo: 'Interacciones', descripcion: 'Atención y seguimiento de clientes', permiso: Permisos.INTERACCIONES_CONSULTAR },
  {
    titulo: 'Usuarios y roles',
    descripcion: 'Usuarios internos y asignación de roles',
    permiso: Permisos.USUARIOS_ADMINISTRAR,
    ruta: '/admin/usuarios',
  },
  { titulo: 'Auditoría', descripcion: 'Registro de accesos y cambios', permiso: Permisos.AUDITORIA_CONSULTAR },
];

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h1>Bienvenido, {{ auth.sesion()?.nombreCompleto }}</h1>
    <p class="subtitulo">Estos son los módulos disponibles para su rol.</p>

    <section class="modulos">
      @for (modulo of modulosPermitidos(); track modulo.titulo) {
        <article class="tarjeta">
          <h2>{{ modulo.titulo }}</h2>
          <p>{{ modulo.descripcion }}</p>
          @if (modulo.ruta) {
            <a [routerLink]="modulo.ruta">Abrir</a>
          } @else {
            <span class="pronto">Disponible próximamente</span>
          }
        </article>
      }
    </section>
  `,
  styles: `
    h1 { margin-bottom: 0.25rem; }
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .modulos { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 1rem; }
    h2 { font-size: 1.05rem; margin: 0 0 0.4rem; color: var(--color-primario); }
    p { color: var(--color-texto-suave); margin: 0 0 0.8rem; }
    .pronto { font-size: 0.85rem; color: var(--color-texto-suave); }
  `,
})
export class InicioComponent {
  readonly auth = inject(AuthService);
  readonly modulosPermitidos = computed(() => {
    const permisos = this.auth.sesion()?.permisos ?? [];
    return MODULOS.filter((modulo) => permisos.includes(modulo.permiso));
  });
}
