// What a scanned code may open. Only this site's own table links count, and only the path is ever followed.
export interface TableLink {
  path: string;
  slug: string;
  n: string;
  eventCode?: string;
}

const RESTAURANT = /^\/t\/([a-z0-9]+)\/table\/(\d{1,3})\/?$/;
const EVENT = /^\/t\/([a-z0-9]+)\/e\/([A-Za-z0-9]{4,12})\/table\/(\d{1,3})\/?$/;

export function tableLinkFrom(text: string, host: string): TableLink | null {
  let url: URL;
  try {
    url = new URL(text.trim());
  } catch {
    return null;
  }
  if ((url.protocol !== 'https:' && url.protocol !== 'http:') || url.hostname !== host) return null;
  const event = EVENT.exec(url.pathname);
  if (event) return { path: `/t/${event[1]}/e/${event[2]}/table/${event[3]}`, slug: event[1], eventCode: event[2], n: event[3] };
  const restaurant = RESTAURANT.exec(url.pathname);
  if (restaurant) return { path: `/t/${restaurant[1]}/table/${restaurant[2]}`, slug: restaurant[1], n: restaurant[2] };
  return null;
}
