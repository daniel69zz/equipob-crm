import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-acceso-denegado',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="tarjeta">
      <h1>Acceso denegado</h1>
      <p>Su rol no tiene permiso para ver esta pantalla. Si cree que es un error, consulte al Administrador de CRM.</p>
      <a routerLink="/">Volver al inicio</a>
    </section>
  `,
  styles: `
    section { max-width: 520px; }
    h1 { margin-top: 0; color: var(--color-error); }
  `,
})
export class AccesoDenegadoComponent {}
