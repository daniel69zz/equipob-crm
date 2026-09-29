import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Trazos de cada ícono (24x24, estilo lineal). */
const TRAZOS: Record<string, string> = {
  inicio: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z',
  clientes:
    'M16 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 17.5V19M10 10.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6M20 19v-1.4a3.3 3.3 0 0 0-2.5-3.2M15.5 4.6a3 3 0 0 1 0 5.8',
  revision: 'M9 12.5l2 2 4-4.5M12 3l7 3v5.5c0 4.5-3 8-7 9.5-4-1.5-7-5-7-9.5V6z',
  eventos: 'M4 7h16M4 12h16M4 17h10M18 15l2 2-2 2',
  usuarios:
    'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1',
  roles: 'M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7zM9 12l2 2 4-4',
  auditoria: 'M9 4h6l1 2h3v15H5V6h3zM9 11h6M9 15h4',
  compras: 'M5 7h14l-1.2 10.1a2 2 0 0 1-2 1.9H8.2a2 2 0 0 1-2-1.9zM9 7V5.5a3 3 0 0 1 6 0V7',
  segmentos: 'M12 3a9 9 0 1 0 9 9h-9zM15 3.5A9 9 0 0 1 20.5 9H15z',
  fidelizacion: 'M12 3l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.4 6.8 19.2l1-5.9L3.5 9.2l5.9-.8z',
  interacciones: 'M4 5h16v10H9l-5 4zM8 9h8M8 12h5',
  inactivos: 'M12 7v5l3 2M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9',
  salir: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10',
  menu: 'M4 6h16M4 12h16M4 18h16',
  flecha: 'M5 12h14M13 6l6 6-6 6',
};

/** Ícono lineal en SVG, del color del texto que lo rodea. */
@Component({
  selector: 'app-icono',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="tamanio()"
      [attr.height]="tamanio()"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path [attr.d]="trazo()" />
    </svg>
  `,
  styles: `
    :host { display: inline-flex; flex-shrink: 0; }
  `,
})
export class IconoComponent {
  readonly nombre = input.required<string>();
  readonly tamanio = input(20);

  readonly trazo = computed(() => TRAZOS[this.nombre()] ?? TRAZOS['inicio']);
}
