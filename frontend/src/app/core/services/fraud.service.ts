import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FraudCase } from '../models/fraud.model';

@Injectable({ providedIn: 'root' })
export class FraudService {
  constructor(private readonly http: HttpClient) {}

  listCases(): Observable<FraudCase[]> {
    return this.http.get<FraudCase[]>(`${environment.apiBaseUrl}/fraud/cases`);
  }
}
