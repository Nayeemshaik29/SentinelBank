import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { Account, LedgerEntry } from '../../core/models/account.model';
import { friendlyErrorMessage } from '../../core/utils/error';
import { formatMoney } from '../../core/utils/money';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css',
})
export class Accounts {
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);

  readonly accounts = signal<Account[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly openingAccount = signal(false);
  readonly showOpenForm = signal(false);
  readonly openForm = this.fb.nonNullable.group({
    currency: ['USD', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
  });

  readonly expandedAccountId = signal<string | null>(null);
  readonly ledgerEntries = signal<LedgerEntry[]>([]);
  readonly ledgerLoading = signal(false);

  readonly formatMoney = formatMoney;

  constructor() {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.accountService.listMine().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  openAccount(): void {
    if (this.openForm.invalid) {
      this.openForm.markAllAsTouched();
      return;
    }
    this.openingAccount.set(true);
    this.accountService.open(this.openForm.getRawValue()).subscribe({
      next: () => {
        this.openingAccount.set(false);
        this.showOpenForm.set(false);
        this.openForm.reset({ currency: 'USD' });
        this.loadAccounts();
      },
      error: (error: unknown) => {
        this.openingAccount.set(false);
        this.errorMessage.set(friendlyErrorMessage(error));
      },
    });
  }

  toggleLedger(accountId: string): void {
    if (this.expandedAccountId() === accountId) {
      this.expandedAccountId.set(null);
      return;
    }
    this.expandedAccountId.set(accountId);
    this.ledgerLoading.set(true);
    this.accountService.ledger(accountId).subscribe({
      next: (entries) => {
        this.ledgerEntries.set(entries);
        this.ledgerLoading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.ledgerLoading.set(false);
      },
    });
  }
}
