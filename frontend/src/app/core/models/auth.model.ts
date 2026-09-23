export type Role = 'CUSTOMER' | 'ANALYST';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/** Decoded from the access token's own JWT claims (see auth-service's JwtService) rather than fetched
 * separately — the token is the source of truth for who the caller is on every request anyway. */
export interface DecodedAccessToken {
  sub: string; // userId
  email: string;
  roles: Role[];
  exp: number; // epoch seconds
}

export type KycStatus = 'PENDING' | 'VERIFIED';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  kycStatus: KycStatus;
  createdAt: string;
}
