import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DecodedAccessToken,
  LoginRequest,
  RegisterRequest,
  Role,
  TokenResponse,
  UserProfile,
} from '../models/auth.model';

const ACCESS_TOKEN_KEY = 'sentinelbank.accessToken';
const REFRESH_TOKEN_KEY = 'sentinelbank.refreshToken';

/**
 * Owns the two tokens and the decoded identity derived from the access token — not a separate profile
 * fetch, since the JWT is already the source of truth for who the caller is on every other request in
 * this project (see the gateway's SecurityConfig). `GET /auth/me` is used only where the fuller profile
 * (full name, KYC status) is actually shown, not for routing/guard decisions.
 *
 * Tokens live in localStorage, which is simple and demo-appropriate but not XSS-hardened: a real deployment
 * would keep the refresh token in an HttpOnly cookie instead. Documented here rather than hidden.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<DecodedAccessToken | null>(this.readStoredUser());

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  constructor(private readonly http: HttpClient) {}

  login(request: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiBaseUrl}/auth/login`, request)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  register(request: RegisterRequest): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${environment.apiBaseUrl}/auth/register`, request);
  }

  refresh(): Observable<TokenResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http
      .post<TokenResponse>(`${environment.apiBaseUrl}/auth/refresh`, { refreshToken })
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      // Best-effort: the tokens are cleared locally either way, so a failed request here (already
      // revoked, network down) must never block the user from actually signing out on their side.
      this.http.post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken }).subscribe({ error: () => {} });
    }
    this.clearTokens();
  }

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${environment.apiBaseUrl}/auth/me`);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  hasRole(role: Role): boolean {
    return this.currentUserSignal()?.roles.includes(role) ?? false;
  }

  private storeTokens(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.currentUserSignal.set(decodeAccessToken(tokens.accessToken));
  }

  private clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.currentUserSignal.set(null);
  }

  private readStoredUser(): DecodedAccessToken | null {
    const token = this.getAccessToken();
    if (!token) {
      return null;
    }
    const decoded = decodeAccessToken(token);
    if (!decoded || decoded.exp * 1000 <= Date.now()) {
      // An expired token left over from a previous visit is not a valid session; the app.config http
      // interceptor will still attempt a refresh on the first real request, this just avoids briefly
      // rendering as "logged in" with a token that would be rejected immediately.
      return null;
    }
    return decoded;
  }
}

/** Reads the claims out of a JWT without verifying the signature — verification is the gateway's job on
 * every request; the frontend only ever uses these claims for routing and display decisions. */
export function decodeAccessToken(token: string): DecodedAccessToken | null {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(normalized)
        .split('')
        .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as DecodedAccessToken;
  } catch {
    return null;
  }
}
