import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { interval, Subscription, take } from 'rxjs';
import { AccountService } from '../../core/services/account.service';
import { TransferService } from '../../core/services/transfer.service';
import { Account } from '../../core/models/account.model';
import { Transfer } from '../../core/models/transfer.model';
import { friendlyErrorMessage } from '../../core/utils/error';
import { formatMoney } from '../../core/utils/money';

const SETTLING_STATUSES = new Set(['PENDING', 'DEBITED', 'COMPENSATING']);

@Component({
  selector: 'app-transfers',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './transfers.html',
  styleUrl: './transfers.css',
})
export class Transfers {
  private readonly fb = inject(FormBuilder);
  private readonly accountService = inject(AccountService);
  private readonly transferService = inject(TransferService);

  readonly accounts = signal<Account[]>([]);
  readonly transfers = signal<Transfer[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    fromAccountId: ['', [Validators.required]],
    toAccountId: ['', [Validators.required, Validators.maxLength(64)]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
  });

  readonly formatMoney = formatMoney;
  private pollSubscription: Subscription | null = null;

  constructor() {
    this.accountService.listMine().subscribe((accounts) => {
      this.accounts.set(accounts);
      if (accounts.length > 0 && !this.form.controls.fromAccountId.value) {
        this.form.controls.fromAccountId.setValue(accounts[0].id);
      }
    });
    this.loadTransfers();
  }

  loadTransfers(): void {
    this.loading.set(true);
    this.transferService.listMine().subscribe({
      next: (transfers) => {
        this.transfers.set(sortNewestFirst(transfers));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const { fromAccountId, toAccountId, amount } = this.form.getRawValue();

    this.transferService
      .create({ fromAccountId, toAccountId, amountMinor: Math.round(amount * 100) })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          // reset(), not patchValue(): patchValue leaves the fields marked touched from the submit that
          // just succeeded, so the now-empty required fields would immediately show as invalid.
          this.form.reset({ fromAccountId, toAccountId: '', amount: 0 });
          this.loadTransfers();
          this.pollUntilSettled();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(friendlyErrorMessage(error));
        },
      });
  }

  currencyFor(accountId: string): string {
    return this.accounts().find((account) => account.id === accountId)?.currency ?? 'USD';
  }

  /** A transfer settles asynchronously (the saga runs over Kafka — see the README), so the row this app
   * just showed as PENDING/DEBITED would otherwise sit stale until the user manually refreshes. Polls a
   * few times, a second apart, and stops as soon as everything visible has reached a terminal status. */
  private pollUntilSettled(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = interval(1000)
      .pipe(take(10))
      .subscribe(() => {
        this.transferService.listMine().subscribe((transfers) => {
          this.transfers.set(sortNewestFirst(transfers));
          if (!transfers.some((transfer) => SETTLING_STATUSES.has(transfer.status))) {
            this.pollSubscription?.unsubscribe();
          }
        });
      });
  }
}

function sortNewestFirst(transfers: Transfer[]): Transfer[] {
  return [...transfers].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}
