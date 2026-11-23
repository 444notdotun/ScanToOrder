import { useId } from 'react';
import type { Receipt } from '@/lib/guest';

// The receipt as a kitchen ticket: torn top and bottom, typewriter lettering, a red PAID stamp.
// Everything is drawn with plain inline styles and hex colours, so the page and the saved picture are the same drawing
// (the picture is made in the browser from this very markup).

const PAPER = '#FBF7EF';
const INK = '#10281F';
const BODY = '#183A2E';
const MUTED = '#6B5E3E';
const RED = '#B93C24';
const TYPEWRITER = "var(--font-typewriter), 'Courier New', Courier, monospace";
const SERIF = "var(--font-young-serif), Georgia, serif";
const HAND = "var(--font-caveat), 'Comic Sans MS', cursive";

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

// "22 NOV 2026 · 7:41 PM". Empty when the receipt has no date.
export function ticketDate(iso: string | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()} · ${hours % 12 || 12}:${minutes} ${hours < 12 ? 'AM' : 'PM'}`;
}

// "13,000" or "13,000.50": the naira sign is kept for the total.
function amount(value: number): string {
  const whole = Number.isInteger(value);
  return value.toLocaleString('en-NG', { minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 });
}

// The torn edge: a row of small bites out of the paper. It is an inline picture, so the saved image keeps it.
function Tear({ edge }: { edge: 'top' | 'bottom' }) {
  const id = useId();
  const path = edge === 'top' ? 'M0 0 A8 8 0 0 0 16 0 V12 H0 Z' : 'M0 12 A8 8 0 0 1 16 12 V0 H0 Z';
  return (
    <svg aria-hidden="true" focusable="false" width="100%" height="12" style={{ display: 'block' }}>
      <defs>
        <pattern id={id} width="16" height="12" patternUnits="userSpaceOnUse">
          <path d={path} fill={PAPER} />
        </pattern>
      </defs>
      <rect width="100%" height="12" fill={`url(#${id})`} />
    </svg>
  );
}

export default function ReceiptTicket({ receipt, businessName, tableName, width }: { receipt: Receipt; businessName: string; tableName: string; width?: number }) {
  const when = ticketDate(receipt.paymentDate);
  const where = [`TABLE ${tableName}`, when].filter(Boolean).join(' · ');
  return (
    <div style={{ width: width ?? '100%', maxWidth: '100%', margin: '0 auto' }}>
      <Tear edge="top" />
      <div style={{ position: 'relative', background: PAPER, color: BODY, padding: '26px 28px 30px' }}>
        <div
          style={{
            position: 'absolute',
            top: 26,
            right: 22,
            transform: 'rotate(-12deg)',
            border: `3px solid ${RED}`,
            color: RED,
            fontFamily: TYPEWRITER,
            fontWeight: 700,
            fontSize: 21,
            letterSpacing: '0.15em',
            padding: '3px 10px',
            borderRadius: 6,
          }}
        >
          PAID
        </div>
        <p style={{ margin: 0, paddingRight: 84, fontFamily: TYPEWRITER, fontSize: 13, letterSpacing: '0.12em', color: MUTED, overflowWrap: 'anywhere' }}>RECEIPT · {receipt.receiptNumber}</p>
        <h2 style={{ margin: '8px 0 8px', paddingRight: 84, fontFamily: SERIF, fontWeight: 400, fontSize: 30, lineHeight: 1.15, color: INK }}>{businessName}</h2>
        <p style={{ margin: '0 0 20px', fontFamily: TYPEWRITER, fontSize: 13, letterSpacing: '0.04em', color: '#4A4A3E' }}>{where}</p>
        <div style={{ borderTop: `2px dashed ${INK}`, borderBottom: `2px dashed ${INK}`, padding: '8px 0', fontFamily: TYPEWRITER, fontSize: 14, lineHeight: 1.45, color: BODY }}>
          {receipt.items.map((item, index) => (
            <div key={index} style={{ padding: '6px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <span style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                  {item.quantity} {item.itemName.toUpperCase()}
                </span>
                <span style={{ flex: 'none' }}>{amount(item.totalPrice)}</span>
              </div>
              {item.specialInstructions ? <div style={{ fontSize: 12, color: MUTED }}>{item.specialInstructions.toUpperCase()}</div> : null}
            </div>
          ))}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '14px 0 4px', fontFamily: TYPEWRITER, fontWeight: 700, fontSize: 21, color: INK }}>
          <span>TOTAL</span>
          <span>₦{amount(receipt.totalPaid)}</span>
        </div>
        <p style={{ margin: '0 0 22px', fontFamily: TYPEWRITER, fontSize: 12, letterSpacing: '0.06em', color: '#4A4A3E' }}>PAID THROUGH PAYSTACK</p>
        <p style={{ margin: 0, textAlign: 'center', fontFamily: HAND, fontWeight: 700, fontSize: 22, lineHeight: 1.3, color: RED }}>Thanks for eating with us!</p>
      </div>
      <Tear edge="bottom" />
    </div>
  );
}
