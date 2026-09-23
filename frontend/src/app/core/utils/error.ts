import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../models/problem-detail.model';

/** Every service in this project reports errors as an RFC 9457 problem response (see
 * problem-detail.model.ts); this pulls the human-readable part out for display, with sensible fallbacks
 * for the cases that never go through that path (a network failure, the gateway's own 429/503/504, or a
 * gateway timeout that didn't get a JSON body at all). */
export function friendlyErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  if (error.status === 0) {
    return 'Could not reach the server. Is it running?';
  }
  const problem = error.error as ProblemDetail | undefined;
  if (problem?.detail) {
    return problem.detail;
  }
  if (error.status === 429) {
    return 'Too many requests — please wait a moment and try again.';
  }
  if (error.status === 503 || error.status === 504) {
    return 'The service is temporarily unavailable. Please try again shortly.';
  }
  return `Request failed (${error.status}).`;
}
