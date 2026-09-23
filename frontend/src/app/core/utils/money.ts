/** Every amount in this project is a whole number of the smallest currency unit (cents for USD), never a
 * float (see the README's account-service section) — this only ever formats for display. */
export function formatMoney(amountMinor: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amountMinor / 100);
}
