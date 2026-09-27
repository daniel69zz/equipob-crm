import { Routes } from '@angular/router';
import { autenticadoGuard, invitadoGuard, permisoGuard } from './core/auth/auth.guards';
import { Permisos } from './core/auth/sesion';
import { AccesoDenegadoComponent } from './features/acceso-denegado/acceso-denegado.component';
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
        path: 'admin/usuarios',
        component: UsuariosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.USUARIOS_ADMINISTRAR },
      },
      { path: 'acceso-denegado', component: AccesoDenegadoComponent },
    ],
  },
  { path: '**', redirectTo: '' },
];
