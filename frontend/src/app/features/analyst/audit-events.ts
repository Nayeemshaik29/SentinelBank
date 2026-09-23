import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuditService } from '../../core/services/audit.service';
import { AuditEvent } from '../../core/models/audit.model';
import { friendlyErrorMessage } from '../../core/utils/error';

@Component({
  selector: 'app-audit-events',
  standalone: true,
  imports: [DatePipe, JsonPipe, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './audit-events.html',
  styleUrl: './analyst.css',
})
export class AuditEvents {
  private readonly auditService = inject(AuditService);

  readonly events = signal<AuditEvent[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly expandedEventId = signal<string | null>(null);

  // A plain property, deliberately not a signal: [(ngModel)] two-way-binds by assignment
  // (`searchTransferId = newValue`), which does not work against a WritableSignal (a signal is read by
  // calling it and written via .set(), not plain assignment) — ngModel would silently never update it.
  searchTransferId = '';

  constructor() {
    this.loadRecent();
  }

  loadRecent(): void {
    this.searchTransferId = '';
    this.loading.set(true);
    this.auditService.recent(200).subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  search(): void {
    const transferId = this.searchTransferId.trim();
    if (!transferId) {
      this.loadRecent();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.auditService.forTransfer(transferId).subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  toggleExpanded(eventId: string): void {
    this.expandedEventId.set(this.expandedEventId() === eventId ? null : eventId);
  }
}
