import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Plately',
    short_name: 'Plately',
    description: 'Scan the code on your table, pick a seat, then order and pay from your phone.',
    start_url: '/',
    display: 'standalone',
    theme_color: '#183A2E',
    background_color: '#183A2E',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  };
}
