// Times as a person writes them: "12:00 am", never "0:00" or "00:00".

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

// "12:00 am", "6:30 pm".
export function clock(iso: string | null | undefined): string {
  const date = parse(iso);
  if (!date) return '';
  const hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours % 12 || 12}:${minutes} ${hours < 12 ? 'am' : 'pm'}`;
}

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// "16 Nov".
export function day(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? `${date.getDate()} ${MONTHS[date.getMonth()]}` : '';
}

// "Mon 16 Nov · 12:00 am".
export function dateTime(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? `${DAYS[date.getDay()]} ${day(iso)} · ${clock(iso)}` : '';
}

// "16 Nov · 12:00 am", for something that happened (no weekday needed).
export function happened(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? `${day(iso)} · ${clock(iso)}` : '';
}
