import { Routes } from '@angular/router';
import { autenticadoGuard, invitadoGuard, permisoGuard } from './core/auth/auth.guards';
import { Permisos } from './core/auth/sesion';
import { AccesoDenegadoComponent } from './features/acceso-denegado/acceso-denegado.component';
import { AuditoriaComponent } from './features/auditoria/auditoria.component';
import { BuscarClientesComponent } from './features/clientes/buscar-clientes.component';
import { HistorialCambiosComponent } from './features/clientes/historial-cambios.component';
import { BitacoraIngestaComponent } from './features/ingesta/bitacora-ingesta.component';
import { RolesComponent } from './features/admin/roles.component';
import { UsuariosComponent } from './features/admin/usuarios.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { LayoutComponent } from './features/layout/layout.component';
import { LoginComponent } from './features/login/login.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [invitadoGuard] },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', component: InicioComponent },
      {
        path: 'clientes',
        component: BuscarClientesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'clientes/:id/historial',
        component: HistorialCambiosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'eventos',
        component: BitacoraIngestaComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.EVENTOS_REPROCESAR },
      },
      {
        path: 'admin/usuarios',
        component: UsuariosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.USUARIOS_ADMINISTRAR },
      },
      {
        path: 'admin/roles',
        component: RolesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.USUARIOS_ADMINISTRAR },
      },
      {
        path: 'admin/auditoria',
        component: AuditoriaComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.AUDITORIA_CONSULTAR },
      },
      { path: 'acceso-denegado', component: AccesoDenegadoComponent },
    ],
  },
  { path: '**', redirectTo: '' },
];
