import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditEvent } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(private readonly http: HttpClient) {}

  recent(limit = 100): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${environment.apiBaseUrl}/audit/events`, {
      params: { limit },
    });
  }

  forTransfer(transferId: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${environment.apiBaseUrl}/audit/events/${transferId}`);
  }
}
