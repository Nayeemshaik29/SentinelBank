export type CaseStatus = 'OPEN' | 'CONFIRMED' | 'CLOSED_FALSE_POSITIVE';

export interface FraudCase {
  transferId: string;
  fromAccountId: string;
  toAccountId: string;
  amountMinor: number;
  currency: string;
  reasons: string[];
  status: CaseStatus;
  createdAt: string;
}
