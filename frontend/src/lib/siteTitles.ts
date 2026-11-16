// The business or event a tab title names, for pages the server renders the head of. It asks the same public
// endpoints the guest's pages do; if the answer is slow or missing the title simply drops the name.
const BACKEND = (process.env.BACKEND_URL || 'https://scantoorder.onrender.com').replace(/\/+$/, '');

async function nameFrom(path: string, field: string): Promise<string | undefined> {
  try {
    const response = await fetch(`${BACKEND}${path}`, { next: { revalidate: 300 }, signal: AbortSignal.timeout(2500) });
    if (!response.ok) return undefined;
    const body = (await response.json()) as { data?: Record<string, unknown> };
    const name = body.data?.[field];
    return typeof name === 'string' && name.trim() ? name : undefined;
  } catch {
    return undefined;
  }
}

export const restaurantName = (slug: string, n: string) => nameFrom(`/api/v1/t/${encodeURIComponent(slug)}/tables/${encodeURIComponent(n)}/seatMap`, 'businessName');

export const eventName = (slug: string, eventCode: string, n: string) =>
  nameFrom(`/api/v1/t/${encodeURIComponent(slug)}/e/${encodeURIComponent(eventCode)}/tables/${encodeURIComponent(n)}`, 'eventName');

export const withName = (label: string, name: string | undefined) => (name ? `${label} · ${name}` : label);
