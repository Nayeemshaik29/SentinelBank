import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** The `/` route has no page of its own — it only ever redirects, to whichever landing page fits the
 * caller's role, so a bookmark to the bare app URL still lands somewhere useful for both roles. */
export const homeRedirectGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const destination = authService.hasRole('ANALYST') ? '/analyst/fraud-cases' : '/accounts';
  return router.createUrlTree([destination]);
};
