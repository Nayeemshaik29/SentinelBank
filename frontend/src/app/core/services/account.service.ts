import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account, CreateAccountRequest, LedgerEntry } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  constructor(private readonly http: HttpClient) {}

  listMine(): Observable<Account[]> {
    return this.http.get<Account[]>(`${environment.apiBaseUrl}/accounts`);
  }

  open(request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${environment.apiBaseUrl}/accounts`, request);
  }

  get(accountId: string): Observable<Account> {
    return this.http.get<Account>(`${environment.apiBaseUrl}/accounts/${accountId}`);
  }

  ledger(accountId: string): Observable<LedgerEntry[]> {
    return this.http.get<LedgerEntry[]>(`${environment.apiBaseUrl}/accounts/${accountId}/ledger`);
  }
}
