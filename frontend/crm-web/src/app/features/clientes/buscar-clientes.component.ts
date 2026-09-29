import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClientesService, ResumenCliente } from './clientes.service';

const NOMBRES_DE_ESTADO: Record<string, string> = {
  COMPLETO: 'Completo',
  INCOMPLETO: 'Incompleto',
  INCONSISTENTE: 'Inconsistente',
};

@Component({
  selector: 'app-buscar-clientes',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <h1>Clientes</h1>
    <p class="subtitulo">Clientes registrados a partir de Marketplace y Ventas. Busque por documento o por su identificador en el módulo.</p>

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
        <label for="idClienteOrigen">ID en Marketplace y Ventas</label>
        <input id="idClienteOrigen" name="idClienteOrigen" [(ngModel)]="filtro.idClienteOrigen" placeholder="CLI-5521" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Buscar</button>
        <button type="button" class="secundario" [disabled]="cargando()" (click)="limpiar()">Limpiar</button>
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
              <th>Actualizado</th>
              <th>Ver</th>
            </tr>
          </thead>
          <tbody>
            @for (cliente of lista; track cliente.id) {
              <tr>
                <td>
                  <span class="cliente">
                    <span class="avatar">{{ iniciales(cliente) }}</span>
                    <span>
                      <strong>{{ cliente.nombres ?? 'Sin nombre' }} {{ cliente.apellidos ?? '' }}</strong>
                      <small>Cliente {{ cliente.id }}</small>
                    </span>
                  </span>
                </td>
                <td>{{ cliente.tipoDocumento ?? '—' }} {{ cliente.numeroDocumento ?? '' }}</td>
                <td><span class="etiqueta" [class]="'perfil-' + cliente.estado">{{ nombreEstado(cliente.estado) }}</span></td>
                <td class="fecha">{{ cliente.actualizadoEn | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td class="vistas">
                  <a [routerLink]="['/clientes', cliente.id, 'historial']">Cambios</a>
                  <a [routerLink]="['/clientes', cliente.id, 'compras']">Compras</a>
                  <a [routerLink]="['/clientes', cliente.id, 'consentimiento']">Consentimiento</a>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio">No se encontraron clientes con esos datos.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .cliente { display: flex; align-items: center; gap: 0.7rem; }
    .cliente strong { display: block; font-weight: 600; white-space: nowrap; }
    .cliente small { white-space: nowrap; }
    .cliente small { color: var(--color-texto-suave); font-size: 0.78rem; }
    .avatar {
      display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
      background: var(--color-primario-claro); color: var(--color-primario); font-weight: 700; font-size: 0.78rem;
    }
    .perfil-COMPLETO { color: var(--color-exito); background: var(--color-exito-claro); }
    .perfil-INCOMPLETO { color: var(--color-aviso); background: var(--color-aviso-claro); }
    .perfil-INCONSISTENTE { color: var(--color-error); background: var(--color-error-claro); }
    .vistas { display: flex; gap: 0.9rem; flex-wrap: wrap; }
    .vacio { color: var(--color-texto-suave); text-align: center; padding: 1.5rem; }
  `,
})
export class BuscarClientesComponent implements OnInit {
  private readonly servicio = inject(ClientesService);

  filtro = { tipoDocumento: '', numeroDocumento: '', idClienteOrigen: '' };

  readonly clientes = signal<ResumenCliente[] | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  /** Al abrir la pantalla se listan los clientes más recientes, sin filtros. */
  ngOnInit(): void {
    this.buscar();
  }

  limpiar(): void {
    this.filtro = { tipoDocumento: '', numeroDocumento: '', idClienteOrigen: '' };
    this.buscar();
  }

  iniciales(cliente: ResumenCliente): string {
    return [cliente.nombres, cliente.apellidos].filter((t): t is string => !!t?.trim())
      .map((t) => t.trim()[0].toUpperCase()).join('') || '?';
  }

  nombreEstado(estado: string): string {
    return NOMBRES_DE_ESTADO[estado] ?? estado;
  }

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
