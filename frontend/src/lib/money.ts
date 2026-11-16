// Naira, as a guest reads it: ₦3,500 or ₦3,500.50.
export function naira(value: number | string | null | undefined): string {
  const amount = typeof value === 'string' ? Number(value) : value ?? 0;
  if (!Number.isFinite(amount)) return '₦0';
  const whole = Number.isInteger(amount);
  return '₦' + amount.toLocaleString('en-NG', { minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 });
}

export function kobo(value: number): string {
  return naira(value / 100);
}

export function clock(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('en-NG', { hour: 'numeric', minute: '2-digit' });
}

export function firstName(name: string | null | undefined): string {
  return (name ?? '').trim().split(/\s+/)[0] || 'friend';
}
