import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FraudService } from '../../core/services/fraud.service';
import { FraudCase } from '../../core/models/fraud.model';
import { friendlyErrorMessage } from '../../core/utils/error';
import { formatMoney } from '../../core/utils/money';

@Component({
  selector: 'app-fraud-cases',
  standalone: true,
  imports: [DatePipe, RouterLink, RouterLinkActive],
  templateUrl: './fraud-cases.html',
  styleUrl: './analyst.css',
})
export class FraudCases {
  private readonly fraudService = inject(FraudService);

  readonly cases = signal<FraudCase[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly formatMoney = formatMoney;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.fraudService.listCases().subscribe({
      next: (cases) => {
        this.cases.set(cases);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.loading.set(false);
      },
    });
  }
}
