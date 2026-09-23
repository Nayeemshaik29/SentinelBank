import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { homeRedirectGuard } from './core/guards/home-redirect.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', canActivate: [homeRedirectGuard], children: [] },
      {
        path: 'accounts',
        canActivate: [roleGuard(['CUSTOMER'])],
        loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
      },
      {
        path: 'transfers',
        canActivate: [roleGuard(['CUSTOMER'])],
        loadComponent: () => import('./features/transfers/transfers').then((m) => m.Transfers),
      },
      {
        path: 'analyst/fraud-cases',
        canActivate: [roleGuard(['ANALYST'])],
        loadComponent: () => import('./features/analyst/fraud-cases').then((m) => m.FraudCases),
      },
      {
        path: 'analyst/audit-events',
        canActivate: [roleGuard(['ANALYST'])],
        loadComponent: () => import('./features/analyst/audit-events').then((m) => m.AuditEvents),
      },
      {
        path: 'forbidden',
        loadComponent: () => import('./features/forbidden/forbidden').then((m) => m.Forbidden),
      },
      { path: '**', redirectTo: '' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
