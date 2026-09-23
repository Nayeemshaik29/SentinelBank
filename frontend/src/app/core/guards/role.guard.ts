import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/auth.model';
import { AuthService } from '../services/auth.service';

/** A CanActivateFn factory, not a bare guard: each protected route passes the role(s) it needs, e.g.
 * `canActivate: [roleGuard(['ANALYST'])]`. Assumes authGuard already ran (see app.routes.ts) — this only
 * decides "wrong role", never "not signed in". */
export function roleGuard(allowedRoles: Role[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (allowedRoles.some((role) => authService.hasRole(role))) {
      return true;
    }
    return router.createUrlTree(['/forbidden']);
  };
}
