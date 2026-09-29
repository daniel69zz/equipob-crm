import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CambioHistorial, ClientesService, PaginaHistorial, PerfilCliente } from './clientes.service';

const TAMANIO_PAGINA = 20;

interface FiltroHistorial {
  campo: string;
  origen: string;
  desde: string;
  hasta: string;
}

const NOMBRES_DE_CAMPO: Record<string, string> = {
  nombres: 'Nombres',
  apellidos: 'Apellidos',
  tipoDocumento: 'Tipo de documento',
  numeroDocumento: 'Número de documento',
  email: 'Correo',
  telefono: 'Teléfono',
  estado: 'Estado del perfil',
  motivosIncompleto: 'Motivos de perfil incompleto',
  identificadoresOrigen: 'Identificadores de origen',
  idClienteConsolidado: 'Unificado en el cliente',
};

const NOMBRES_DE_TIPO: Record<CambioHistorial['tipo'], string> = {
  CREACION: 'Alta',
  ACTUALIZACION: 'Actualización',
  VINCULACION: 'Vinculación',
  UNIFICACION: 'Unificación',
};

const NOMBRES_DE_ORIGEN: Record<CambioHistorial['origen'], string> = {
  VENTAS: 'Ventas',
  MARKETPLACE: 'Marketplace',
  CRM: 'CRM (manual)',
};

@Component({
  selector: 'app-historial-cambios',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <p><a routerLink="/clientes">← Clientes</a></p>
    <h1>Historial de cambios</h1>
    @if (perfil(); as p) {
      <p class="subtitulo">
        {{ p.nombres }} {{ p.apellidos }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }} · cliente {{ p.id }}
        @if (p.idClienteConsolidado) {
          · unificado en el cliente <a [routerLink]="['/clientes', p.idClienteConsolidado, 'historial']">{{ p.idClienteConsolidado }}</a>
        }
      </p>
    }

    <form class="tarjeta filtros" (ngSubmit)="buscar(0)">
      <div>
        <label for="campo">Campo</label>
        <select id="campo" name="campo" [(ngModel)]="filtro.campo">
          <option value="">Todos</option>
          @for (campo of campos; track campo.codigo) {
            <option [value]="campo.codigo">{{ campo.nombre }}</option>
          }
        </select>
      </div>
      <div>
        <label for="origen">Origen</label>
        <select id="origen" name="origen" [(ngModel)]="filtro.origen">
          <option value="">Todos</option>
          <option value="VENTAS">Ventas</option>
          <option value="MARKETPLACE">Marketplace</option>
          <option value="CRM">CRM (manual)</option>
        </select>
      </div>
      <div>
        <label for="desde">Desde</label>
        <input id="desde" name="desde" type="date" [(ngModel)]="filtro.desde" />
      </div>
      <div>
        <label for="hasta">Hasta</label>
        <input id="hasta" name="hasta" type="date" [(ngModel)]="filtro.hasta" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Filtrar</button>
        <button type="button" class="secundario" (click)="limpiar()">Limpiar</button>
      </div>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (historial(); as datos) {
      @for (cambio of datos.cambios; track cambio.id) {
        <article class="tarjeta cambio">
          <header>
            <span class="fecha">{{ cambio.fecha | date: 'dd/MM/yyyy HH:mm:ss' }}</span>
            <span class="etiqueta" [class]="'origen-' + cambio.origen">{{ nombreOrigen(cambio.origen) }}</span>
            <strong>{{ nombreTipo(cambio.tipo) }}</strong>
            <span class="responsable">por {{ cambio.responsable }}</span>
          </header>
          <table>
            <thead>
              <tr>
                <th>Campo</th>
                <th>Valor anterior</th>
                <th>Valor nuevo</th>
              </tr>
            </thead>
            <tbody>
              @for (campo of cambio.campos; track campo.campo) {
                <tr>
                  <td>{{ nombreCampo(campo.campo) }}</td>
                  <td class="valor anterior">{{ campo.anterior ?? '—' }}</td>
                  <td class="valor">{{ campo.nuevo ?? '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </article>
      } @empty {
        <p class="tarjeta">No hay cambios registrados para estos filtros.</p>
      }

      @if (datos.total > 0) {
        <div class="paginacion">
          <span>{{ desdeRegistro() }}–{{ hastaRegistro() }} de {{ datos.total }}</span>
          <button type="button" class="secundario" [disabled]="datos.pagina === 0 || cargando()" (click)="buscar(datos.pagina - 1)">
            Más recientes
          </button>
          <button type="button" class="secundario" [disabled]="esUltimaPagina() || cargando()" (click)="buscar(datos.pagina + 1)">
            Más antiguos
          </button>
        </div>
      }
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .cambio { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    .cambio header { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
    .fecha { font-family: monospace; }
    .responsable { color: var(--color-texto-suave); }
    .etiqueta { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: #eef2f7; }
    .origen-VENTAS { color: #1f3864; background: #dae8fc; }
    .origen-MARKETPLACE { color: #6b3fa0; background: #efe6fb; }
    .origen-CRM { color: #9a6700; background: #fff4e0; }
    .valor { word-break: break-word; }
    .anterior { color: var(--color-texto-suave); text-decoration: line-through; }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; }
  `,
})
export class HistorialCambiosComponent implements OnInit {
  private readonly servicio = inject(ClientesService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/historial. */
  readonly id = input.required<string>();

  readonly campos = [
    ...Object.entries(NOMBRES_DE_CAMPO).map(([codigo, nombre]) => ({ codigo, nombre })),
    { codigo: 'direcciones', nombre: 'Direcciones' },
  ];
  filtro = HistorialCambiosComponent.filtroVacio();

  readonly perfil = signal<PerfilCliente | null>(null);
  readonly historial = signal<PaginaHistorial | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly desdeRegistro = computed(() => (this.historial()?.pagina ?? 0) * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(
    () => (this.historial()?.pagina ?? 0) * TAMANIO_PAGINA + (this.historial()?.cambios.length ?? 0),
  );
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= (this.historial()?.total ?? 0));

  ngOnInit(): void {
    this.servicio.obtener(this.idCliente()).subscribe({
      next: (perfil) => this.perfil.set(perfil),
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
    this.buscar(0);
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.historial(this.idCliente(), { ...this.filtro }, pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.historial.set(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mostrarError(e);
      },
    });
  }

  limpiar(): void {
    this.filtro = HistorialCambiosComponent.filtroVacio();
    this.buscar(0);
  }

  nombreCampo(campo: string): string {
    const direccion = /^direcciones\[(.+)\]\.(.+)$/.exec(campo);
    if (direccion) {
      return `Dirección ${direccion[1]} · ${direccion[2]}`;
    }
    return NOMBRES_DE_CAMPO[campo] ?? campo;
  }

  nombreTipo(tipo: CambioHistorial['tipo']): string {
    return NOMBRES_DE_TIPO[tipo] ?? tipo;
  }

  nombreOrigen(origen: CambioHistorial['origen']): string {
    return NOMBRES_DE_ORIGEN[origen] ?? origen;
  }

  private idCliente(): number {
    return Number(this.id());
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudo consultar el historial de cambios.');
  }

  private static filtroVacio(): FiltroHistorial {
    return { campo: '', origen: '', desde: '', hasta: '' };
  }
}
