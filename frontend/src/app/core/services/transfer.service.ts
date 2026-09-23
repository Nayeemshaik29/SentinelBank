import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateTransferRequest, Transfer } from '../models/transfer.model';

@Injectable({ providedIn: 'root' })
export class TransferService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Generates the Idempotency-Key itself (see the README's transfer section): one fresh key per call, so
   * a genuine double-submit from this method call always debits once. A *user* retrying the same logical
   * transfer (e.g. after seeing an error) gets a fresh key too, deliberately — this method has no notion
   * of "the same transfer as before"; the form component is what would need to reuse a key across retries
   * of what the user considers one attempt, and this app does not do that (simpler, and a demo term is
   * more useful showing a in visibly new transfer per submit than hiding retries as no-ops).
   */
  create(request: CreateTransferRequest): Observable<Transfer> {
    const headers = new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() });
    return this.http.post<Transfer>(`${environment.apiBaseUrl}/transfers`, request, { headers });
  }

  listMine(): Observable<Transfer[]> {
    return this.http.get<Transfer[]>(`${environment.apiBaseUrl}/transfers`);
  }

  get(transferId: string): Observable<Transfer> {
    return this.http.get<Transfer>(`${environment.apiBaseUrl}/transfers/${transferId}`);
  }
}
