import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

type RefreshState = { status: 'pending' } | { status: 'succeeded'; token: string } | { status: 'failed' };

// Module-level, deliberately not a service field: every concurrent request that hits a 401 while a
// refresh is already in flight waits on the SAME refresh instead of each firing its own — a burst of
// requests right as the access token expires must produce one /auth/refresh call, not N of them, since
// the auth-service's refresh token is single-use (see the README's "refresh tokens are single-use"
// security note) and a second concurrent refresh would revoke the first one's brand-new token.
let refreshInProgress = false;
const refreshState$ = new BehaviorSubject<RefreshState>({ status: 'failed' });

const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh'];

export function authInterceptor(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.includes(path));
  const withAuth = addHeaders(req, authService.getAccessToken());

  return next(withAuth).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint) {
        return throwError(() => error);
      }
      return handleUnauthorized(req, next, authService, router);
    }),
  );
}

function handleUnauthorized(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
  router: Router,
): Observable<HttpEvent<unknown>> {
  if (!authService.getRefreshToken()) {
    authService.logout();
    router.navigate(['/login']);
    return throwError(() => new Error('Not signed in'));
  }

  if (!refreshInProgress) {
    refreshInProgress = true;
    refreshState$.next({ status: 'pending' });
    authService.refresh().subscribe({
      next: (tokens) => {
        refreshInProgress = false;
        refreshState$.next({ status: 'succeeded', token: tokens.accessToken });
      },
      error: () => {
        refreshInProgress = false;
        refreshState$.next({ status: 'failed' });
        authService.logout();
        router.navigate(['/login']);
      },
    });
  }

  return refreshState$.pipe(
    filter((state) => state.status !== 'pending'),
    take(1),
    switchMap((state) =>
      state.status === 'succeeded'
        ? next(addHeaders(req, state.token))
        : throwError(() => new Error('Session expired')),
    ),
  );
}

function addHeaders(req: HttpRequest<unknown>, accessToken: string | null): HttpRequest<unknown> {
  let headers = req.headers.set('X-Correlation-Id', crypto.randomUUID());
  if (accessToken) {
    headers = headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return req.clone({ headers });
}
