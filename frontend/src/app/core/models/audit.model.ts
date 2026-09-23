export interface AuditEvent {
  eventId: string;
  eventType: string;
  aggregateId: string;
  occurredAt: string;
  correlationId: string;
  payload: Record<string, unknown>;
}
