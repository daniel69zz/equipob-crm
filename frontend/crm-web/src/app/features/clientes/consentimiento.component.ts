import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { SiTienePermisoDirective } from '../../core/auth/si-tiene-permiso.directive';
import { Permisos } from '../../core/auth/sesion';
import { ClientesService, PerfilCliente } from './clientes.service';
import {
  AlcanceConsentimiento,
  CanalConsentimiento,
  Consentimiento,
  ConsentimientoService,
  OperacionConsentimiento,
  RegistroConsentimiento,
} from './consentimiento.service';

const ALCANCES: { codigo: AlcanceConsentimiento; nombre: string }[] = [
  { codigo: 'GESTION_CLIENTE', nombre: 'Gestión del cliente' },
  { codigo: 'ANALISIS_COMPORTAMIENTO', nombre: 'Análisis de comportamiento' },
  { codigo: 'SEGMENTACION', nombre: 'Segmentación' },
  { codigo: 'FIDELIZACION', nombre: 'Fidelización' },
  { codigo: 'COMUNICACIONES_COMERCIALES', nombre: 'Comunicaciones comerciales' },
];

const CANALES: { codigo: CanalConsentimiento; nombre: string }[] = [
  { codigo: 'PRESENCIAL', nombre: 'Presencial' },
  { codigo: 'MARKETPLACE_VENTAS', nombre: 'Marketplace y Ventas' },
  { codigo: 'TELEFONICO', nombre: 'Telefónico' },
  { codigo: 'CORREO', nombre: 'Correo' },
];

const NOMBRES_DE_OPERACION: Record<OperacionConsentimiento, string> = {
  OTORGAMIENTO: 'Otorgamiento',
  ACTUALIZACION: 'Actualización',
  REVOCACION: 'Revocación',
};

interface Formulario {
  canal: CanalConsentimiento;
  alcances: Record<AlcanceConsentimiento, boolean>;
  vigenciaDesde: string;
  vigenciaHasta: string;
}

/**
 * Consulta y edición del consentimiento de tratamiento de datos de un cliente (SCRUM-179). Ver y
 * consultar el historial exige CLIENTE_CONSULTAR; otorgar, actualizar y revocar, CLIENTE_EDITAR.
 */
@Component({
  selector: 'app-consentimiento',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, SiTienePermisoDirective, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="id()" />
    <h1>Consentimiento de datos</h1>
    @if (perfil(); as p) {
      <p class="subtitulo">{{ p.nombres }} {{ p.apellidos }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }} · cliente {{ p.id }}</p>
    }

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }
    @if (mensaje()) {
      <p class="exito" role="status">{{ mensaje() }}</p>
    }

    <section class="tarjeta">
      <h2>Estado actual</h2>
      @if (consentimiento(); as c) {
        <p>
          <span class="etiqueta" [class]="c.vigente ? 'vigente' : 'no-vigente'">
            {{ c.vigente ? 'Vigente' : c.estado === 'REVOCADO' ? 'Revocado' : 'Fuera de vigencia' }}
          </span>
        </p>
        <dl>
          <dt>Canal</dt>
          <dd>{{ nombreCanal(c.canal) }}</dd>
          <dt>Alcance autorizado</dt>
          <dd>{{ nombresAlcances(c.alcances) }}</dd>
          <dt>Otorgado el</dt>
          <dd>{{ c.fechaOtorgamiento | date: 'dd/MM/yyyy HH:mm' }}</dd>
          <dt>Vigencia</dt>
          <dd>{{ c.vigenciaDesde | date: 'dd/MM/yyyy' }} – {{ c.vigenciaHasta ? (c.vigenciaHasta | date: 'dd/MM/yyyy') : 'sin vencimiento' }}</dd>
          @if (c.fechaRevocacion) {
            <dt>Revocado el</dt>
            <dd>{{ c.fechaRevocacion | date: 'dd/MM/yyyy HH:mm' }}{{ c.motivoRevocacion ? ' · ' + c.motivoRevocacion : '' }}</dd>
          }
          <dt>Último cambio</dt>
          <dd>{{ c.actualizadoEn | date: 'dd/MM/yyyy HH:mm' }} por {{ c.actualizadoPor }}</dd>
        </dl>

        @if (c.estado === 'OTORGADO') {
          <form *appSiTienePermiso="permisos.CLIENTE_EDITAR" class="revocar" (ngSubmit)="revocar()">
            <label for="motivo">Motivo de la revocación (opcional)</label>
            <input id="motivo" name="motivo" maxlength="300" [(ngModel)]="motivo" />
            <button type="submit" class="peligro" [disabled]="guardando()">Revocar consentimiento</button>
          </form>
        }
      } @else if (!cargando()) {
        <p>El cliente todavía no registró un consentimiento. Sin él, el CRM no puede usar sus datos para ninguna finalidad.</p>
      }
    </section>

    <form *appSiTienePermiso="permisos.CLIENTE_EDITAR" class="tarjeta" (ngSubmit)="guardar()">
      <h2>{{ consentimiento()?.estado === 'OTORGADO' ? 'Actualizar consentimiento' : 'Registrar consentimiento' }}</h2>
      <div class="campos">
        <div>
          <label for="canal">Canal</label>
          <select id="canal" name="canal" [(ngModel)]="formulario.canal">
            @for (canal of canales; track canal.codigo) {
              <option [value]="canal.codigo">{{ canal.nombre }}</option>
            }
          </select>
        </div>
        <div>
          <label for="vigenciaDesde">Vigente desde</label>
          <input id="vigenciaDesde" name="vigenciaDesde" type="date" [(ngModel)]="formulario.vigenciaDesde" />
        </div>
        <div>
          <label for="vigenciaHasta">Vigente hasta (vacío: no vence)</label>
          <input id="vigenciaHasta" name="vigenciaHasta" type="date" [(ngModel)]="formulario.vigenciaHasta" />
        </div>
      </div>
      <fieldset>
        <legend>Alcance autorizado</legend>
        @for (alcance of alcances; track alcance.codigo) {
          <label class="opcion">
            <input type="checkbox" [name]="alcance.codigo" [(ngModel)]="formulario.alcances[alcance.codigo]" />
            {{ alcance.nombre }}
          </label>
        }
      </fieldset>
      <button type="submit" [disabled]="guardando()">Guardar</button>
    </form>

    <section class="tarjeta">
      <h2>Historial</h2>
      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Operación</th>
            <th>Canal</th>
            <th>Alcance</th>
            <th>Vigencia</th>
            <th>Responsable</th>
          </tr>
        </thead>
        <tbody>
          @for (registro of historial(); track registro.id) {
            <tr>
              <td class="fecha">{{ registro.fecha | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
              <td>
                {{ nombreOperacion(registro.operacion) }}
                @if (registro.motivoRevocacion) {
                  <div class="motivo">{{ registro.motivoRevocacion }}</div>
                }
              </td>
              <td>{{ nombreCanal(registro.canal) }}</td>
              <td>{{ nombresAlcances(registro.alcances) }}</td>
              <td>{{ registro.vigenciaDesde | date: 'dd/MM/yyyy' }} – {{ registro.vigenciaHasta ? (registro.vigenciaHasta | date: 'dd/MM/yyyy') : '∞' }}</td>
              <td>{{ registro.responsable }}</td>
            </tr>
          } @empty {
            <tr><td colspan="6">Sin cambios registrados.</td></tr>
          }
        </tbody>
      </table>
    </section>
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    section.tarjeta, form.tarjeta { margin-bottom: 1rem; }
    h2 { font-size: 1.1rem; margin-top: 0; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.35rem 1rem; margin: 0 0 1rem; }
    dt { color: var(--color-texto-suave); }
    dd { margin: 0; }
    .etiqueta { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
    .vigente { color: var(--color-exito); background: var(--color-exito-claro); }
    .no-vigente { color: var(--color-error); background: var(--color-error-claro); }
    .campos { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 1rem; }
    fieldset { border: 1px solid var(--color-borde); border-radius: 6px; margin: 1rem 0; }
    .opcion { display: flex; align-items: center; gap: 0.5rem; font-weight: normal; }
    .opcion input { width: auto; }
    .revocar { display: flex; flex-wrap: wrap; align-items: end; gap: 0.75rem; }
    .revocar input { flex: 1 1 240px; }
    button.peligro { background: var(--color-error); border-color: var(--color-error); }
    .exito { color: var(--color-exito); }
    .fecha { font-family: monospace; }
    .motivo { color: var(--color-texto-suave); font-size: 0.85rem; }
  `,
})
export class ConsentimientoComponent implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly servicio = inject(ConsentimientoService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/consentimiento. */
  readonly id = input.required<string>();

  readonly permisos = Permisos;
  readonly alcances = ALCANCES;
  readonly canales = CANALES;

  formulario = ConsentimientoComponent.formularioVacio();
  motivo = '';

  readonly perfil = signal<PerfilCliente | null>(null);
  readonly consentimiento = signal<Consentimiento | null>(null);
  readonly historial = signal<RegistroConsentimiento[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mensaje = signal<string | null>(null);

  ngOnInit(): void {
    this.clientes.obtener(this.idCliente()).subscribe({
      next: (perfil) => this.perfil.set(perfil),
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
    this.cargar();
  }

  guardar(): void {
    const alcances = ALCANCES.map((a) => a.codigo).filter((codigo) => this.formulario.alcances[codigo]);
    if (alcances.length === 0) {
      this.error.set('Seleccione al menos un alcance.');
      return;
    }
    this.enviar(
      this.servicio.registrar(this.idCliente(), {
        canal: this.formulario.canal,
        alcances,
        vigenciaDesde: this.formulario.vigenciaDesde || null,
        vigenciaHasta: this.formulario.vigenciaHasta || null,
      }),
      'Consentimiento guardado.',
    );
  }

  revocar(): void {
    this.enviar(this.servicio.revocar(this.idCliente(), this.motivo), 'Consentimiento revocado.');
  }

  nombreCanal(canal: CanalConsentimiento): string {
    return CANALES.find((c) => c.codigo === canal)?.nombre ?? canal;
  }

  nombresAlcances(alcances: AlcanceConsentimiento[]): string {
    return alcances.map((a) => ALCANCES.find((x) => x.codigo === a)?.nombre ?? a).join(', ');
  }

  nombreOperacion(operacion: OperacionConsentimiento): string {
    return NOMBRES_DE_OPERACION[operacion] ?? operacion;
  }

  private enviar(peticion: ReturnType<ConsentimientoService['registrar']>, exito: string): void {
    this.guardando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.motivo = '';
        this.mensaje.set(exito);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.mostrarError(e);
      },
    });
  }

  private cargar(): void {
    this.cargando.set(true);
    this.servicio.consultar(this.idCliente()).subscribe({
      next: (consentimiento) => {
        this.consentimiento.set(consentimiento);
        this.formulario = ConsentimientoComponent.formularioDesde(consentimiento);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.consentimiento.set(null);
        if (e.status !== 404) {
          this.mostrarError(e);
        }
      },
    });
    this.servicio.historial(this.idCliente()).subscribe({
      next: (registros) => this.historial.set(registros),
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
  }

  private idCliente(): number {
    return Number(this.id());
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudo completar la operación con el consentimiento.');
  }

  /** Un consentimiento revocado se vuelve a otorgar con fechas nuevas, así que no se precargan. */
  private static formularioDesde(c: Consentimiento): Formulario {
    const formulario = ConsentimientoComponent.formularioVacio();
    formulario.canal = c.canal;
    c.alcances.forEach((a) => (formulario.alcances[a] = true));
    if (c.estado === 'OTORGADO') {
      formulario.vigenciaDesde = c.vigenciaDesde;
      formulario.vigenciaHasta = c.vigenciaHasta ?? '';
    }
    return formulario;
  }

  private static formularioVacio(): Formulario {
    return {
      canal: 'PRESENCIAL',
      alcances: {
        GESTION_CLIENTE: false,
        ANALISIS_COMPORTAMIENTO: false,
        SEGMENTACION: false,
        FIDELIZACION: false,
        COMUNICACIONES_COMERCIALES: false,
      },
      vigenciaDesde: '',
      vigenciaHasta: '',
    };
  }
}
