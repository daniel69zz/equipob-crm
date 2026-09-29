import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClientesService, PerfilCliente } from './clientes.service';
import { CategoriaConsumida, ComprasService } from './compras.service';

/**
 * Categorías que más consume un cliente, de mayor a menor monto (SCRUM-18). Combina los
 * identificadores por canal del perfil (GET /api/perfil/clientes/{id}) con la consulta de
 * servicio-comportamiento, tal como lo describe docs/compra/categorias-mas-consumidas.md.
 */
@Component({
  selector: 'app-categorias-consumidas',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  template: `
    <p><a routerLink="/clientes">← Clientes</a> · <a [routerLink]="['/clientes', id(), 'compras']">Historial de compras</a></p>
    <h1>Categorías más consumidas</h1>
    @if (perfil(); as p) {
      <p class="subtitulo">
        {{ p.nombres }} {{ p.apellidos }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }} · cliente {{ p.id }}
      </p>
    }

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (categorias(); as lista) {
      @if (lista.length === 0) {
        <p class="tarjeta">Este cliente no tiene compras vigentes con categorías.</p>
      } @else {
        <div class="tarjeta">
          <p class="criterio">Ordenadas por monto vigente; a igual monto, por cantidad de compras.</p>
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Categoría</th>
                <th>Compras</th>
                <th>Unidades</th>
                <th>Monto</th>
                <th>% del monto</th>
              </tr>
            </thead>
            <tbody>
              @for (fila of lista; track fila.categoria; let posicion = $index) {
                <tr>
                  <td>{{ posicion + 1 }}</td>
                  <td>
                    {{ fila.categoria }}
                    @if (fila.sinCategoria) {
                      <span class="aviso" title="Hay ítems sin categoría: corrígelos en la bitácora de ingesta">Revisar</span>
                    }
                  </td>
                  <td>{{ fila.compras }}</td>
                  <td>{{ fila.unidades }}</td>
                  <td>{{ fila.monto | number: '1.2-2' }}</td>
                  <td>
                    <span class="barra" [style.width.%]="porcentaje(fila)" aria-hidden="true"></span>
                    {{ porcentaje(fila) | number: '1.0-1' }}%
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else if (!error()) {
      <p>Calculando…</p>
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .criterio { color: var(--color-texto-suave); margin-top: 0; }
    .aviso { margin-left: 0.5rem; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; color: #9a6700; background: #fff4e0; }
    .barra { display: inline-block; height: 0.5rem; max-width: 6rem; margin-right: 0.5rem; border-radius: 999px; background: #dae8fc; vertical-align: middle; }
  `,
})
export class CategoriasConsumidasComponent implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly compras = inject(ComprasService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/categorias. */
  readonly id = input.required<string>();

  readonly perfil = signal<PerfilCliente | null>(null);
  readonly categorias = signal<CategoriaConsumida[] | null>(null);
  readonly error = signal<string | null>(null);

  private readonly montoTotal = computed(() => (this.categorias() ?? []).reduce((suma, c) => suma + c.monto, 0));

  ngOnInit(): void {
    this.clientes.obtener(Number(this.id())).subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        // El servicio de comportamiento solo conoce los identificadores de Marketplace y Ventas.
        const identificadores = perfil.identificadoresOrigen.filter((id) => id.origen !== 'CRM');
        this.compras.categorias(Number(this.id()), identificadores).subscribe({
          next: (resultado) => this.categorias.set(resultado.categorias),
          error: (e: HttpErrorResponse) => this.mostrarError(e),
        });
      },
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
  }

  porcentaje(fila: CategoriaConsumida): number {
    const total = this.montoTotal();
    return total > 0 ? (fila.monto / total) * 100 : 0;
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudieron consultar las categorías más consumidas.');
  }
}
