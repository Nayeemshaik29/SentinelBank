export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type EntryType = 'DEBIT' | 'CREDIT';

export interface Account {
  id: string;
  ownerId: string;
  accountNumber: string;
  currency: string;
  balanceMinor: number;
  status: AccountStatus;
  createdAt: string;
}

export interface CreateAccountRequest {
  currency: string;
}

export interface LedgerEntry {
  id: string;
  accountId: string;
  entryType: EntryType;
  amountMinor: number;
  balanceAfter: number;
  referenceId: string;
  description: string;
  createdAt: string;
}
