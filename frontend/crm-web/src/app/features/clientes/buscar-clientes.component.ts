import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClientesService, ResumenCliente } from './clientes.service';

@Component({
  selector: 'app-buscar-clientes',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <h1>Clientes</h1>
    <p class="subtitulo">Busque un cliente por su documento o por su identificador en Marketplace o Ventas.</p>

    <form class="tarjeta filtros" (ngSubmit)="buscar()">
      <div>
        <label for="tipoDocumento">Tipo de documento</label>
        <select id="tipoDocumento" name="tipoDocumento" [(ngModel)]="filtro.tipoDocumento">
          <option value="">—</option>
          <option value="CI">CI</option>
          <option value="NIT">NIT</option>
          <option value="PASAPORTE">Pasaporte</option>
          <option value="CE">CE</option>
        </select>
      </div>
      <div>
        <label for="numeroDocumento">Número de documento</label>
        <input id="numeroDocumento" name="numeroDocumento" [(ngModel)]="filtro.numeroDocumento" placeholder="4455667" />
      </div>
      <div>
        <label for="origen">Sistema</label>
        <select id="origen" name="origen" [(ngModel)]="filtro.origen">
          <option value="">—</option>
          <option value="VENTAS">Ventas</option>
          <option value="MARKETPLACE">Marketplace</option>
        </select>
      </div>
      <div>
        <label for="idClienteOrigen">Identificador en ese sistema</label>
        <input id="idClienteOrigen" name="idClienteOrigen" [(ngModel)]="filtro.idClienteOrigen" placeholder="CLI-5521" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Buscar</button>
      </div>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (clientes(); as lista) {
      <div class="tarjeta">
        <table>
          <thead>
            <tr>
              <th>Cliente</th>
              <th>Documento</th>
              <th>Perfil</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (cliente of lista; track cliente.id) {
              <tr>
                <td>{{ cliente.nombres }} {{ cliente.apellidos }}</td>
                <td>{{ cliente.tipoDocumento }} {{ cliente.numeroDocumento }}</td>
                <td>{{ cliente.estado === 'COMPLETO' ? 'Completo' : 'Incompleto' }}</td>
                <td>
                  <a [routerLink]="['/clientes', cliente.id, 'historial']">Historial de cambios</a> ·
                  <a [routerLink]="['/clientes', cliente.id, 'compras']">Historial de compras</a> ·
                  <a [routerLink]="['/clientes', cliente.id, 'categorias']">Categorías</a> ·
                  <a [routerLink]="['/clientes', cliente.id, 'consentimiento']">Consentimiento</a>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="4">No se encontraron clientes.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
  `,
})
export class BuscarClientesComponent {
  private readonly servicio = inject(ClientesService);

  filtro = { tipoDocumento: '', numeroDocumento: '', origen: '', idClienteOrigen: '' };

  readonly clientes = signal<ResumenCliente[] | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  buscar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.buscar(this.filtro).subscribe({
      next: (resultado) => {
        this.clientes.set(resultado.clientes);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo buscar clientes.');
      },
    });
  }
}
