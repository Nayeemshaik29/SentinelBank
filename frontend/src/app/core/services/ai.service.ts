import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AskRequest, AskResponse } from '../models/ai.model';

@Injectable({ providedIn: 'root' })
export class AiService {
  constructor(private readonly http: HttpClient) {}

  ask(request: AskRequest): Observable<AskResponse> {
    return this.http.post<AskResponse>(`${environment.apiBaseUrl}/ai/ask`, request);
  }
}
