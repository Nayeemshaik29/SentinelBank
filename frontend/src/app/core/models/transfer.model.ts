export type TransferStatus = 'PENDING' | 'DEBITED' | 'FAILED' | 'COMPLETED' | 'COMPENSATING' | 'REVERSED';

export interface Transfer {
  id: string;
  ownerId: string;
  fromAccountId: string;
  toAccountId: string;
  currency: string;
  amountMinor: number;
  status: TransferStatus;
  failureCode: string | null;
  failureDetail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amountMinor: number;
}
