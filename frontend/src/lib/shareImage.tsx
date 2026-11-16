import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { ImageResponse } from 'next/og';

export const SHARE_SIZE = { width: 1200, height: 630 };

// The etched eating pattern from the app, as one picture to tile behind the card.
const PATTERN = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630"><defs><pattern id="p" width="120" height="120" patternUnits="userSpaceOnUse" patternTransform="rotate(-12)"><g fill="none" stroke="#F4ECD8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.17"><path d="M16 12v10a4 4 0 0 0 8 0V12M20 12v34"/><path d="M80 14h16c0 9-3 14-8 14s-8-5-8-14zM88 28v14M82 42h12"/><ellipse cx="24" cy="74" rx="5" ry="7"/><path d="M24 81v24"/><path d="M52 52c5 5 6 14 3 22h-3V52zM52 74v16"/><path d="M78 106c8-8 14-18 18-30"/><path d="M84 99c-5-1-7-4-7-8c4 0 7 3 7 8zM89 91c1-5 4-7 8-7c0 4-3 7-8 7zM91 84c-5-1-7-4-7-8c4 0 7 3 7 8z"/><path d="M50 20c3-3 7-3 10 0M50 26c3-3 7-3 10 0"/></g></pattern></defs><rect width="100%" height="100%" fill="url(#p)"/></svg>`;

// The picture a shared link shows (WhatsApp, X, iMessage, Slack): the green ground, the pattern, the logo and the line.
export async function shareImage() {
  const font = await readFile(join(process.cwd(), 'src/assets/YoungSerif-Regular.ttf'));
  const pattern = `data:image/svg+xml;base64,${Buffer.from(PATTERN).toString('base64')}`;
  return new ImageResponse(
    (
      <div style={{ width: '100%', height: '100%', display: 'flex', position: 'relative', background: '#183A2E', color: '#F4ECD8', fontFamily: 'Young Serif' }}>
        <div style={{ position: 'absolute', left: 0, top: 0, width: 1200, height: 630, backgroundImage: `url(${pattern})` }} />
        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '0 90px', width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <svg width="104" height="104" viewBox="0 0 120 120" fill="none">
              <rect x="20" y="12" width="18" height="96" rx="9" fill="#F4ECD8" />
              <circle cx="70" cy="48" r="31" stroke="#F4ECD8" strokeWidth="10" />
              <circle cx="70" cy="48" r="16" stroke="#F0B94A" strokeWidth="7" />
            </svg>
            <div style={{ fontSize: 76, marginLeft: 18, letterSpacing: -2 }}>plately</div>
          </div>
          <div style={{ fontSize: 112, lineHeight: 1.04, marginTop: 34, whiteSpace: "nowrap", letterSpacing: -2 }}>Scan. Order. Eat.</div>
          <div style={{ fontSize: 38, marginTop: 26, color: '#F0B94A' }}>No app to download. No account to make.</div>
        </div>
      </div>
    ),
    { ...SHARE_SIZE, fonts: [{ name: 'Young Serif', data: font, style: 'normal', weight: 400 }] },
  );
}
