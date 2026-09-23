/** The RFC 9457 shape every service in this project reports errors in (see common's GlobalExceptionHandler
 * and the gateway's ProblemResponses). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail: string;
  code?: string;
  fields?: Record<string, string>;
}
