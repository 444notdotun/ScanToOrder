import { SHARE_SIZE, shareImage } from '@/lib/shareImage';

export const alt = 'Plately: Scan. Order. Eat.';
export const size = SHARE_SIZE;
export const contentType = 'image/png';

export default function Image() {
  return shareImage();
}
