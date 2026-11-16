import type { ReactNode } from 'react';

// Small line icons for the bottom bar: 22px, drawn with the text colour so a current item turns cream with its label.
function Icon({ children }: { children: ReactNode }) {
  return (
    <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" focusable="false">
      {children}
    </svg>
  );
}

export const icons = {
  menu: (
    <Icon>
      <path d="M7 3v8a2 2 0 0 0 2 2v8M7 3v6M11 3v6M5 3v6a2 2 0 0 0 2 2" />
      <path d="M17 3c-2 1.5-3 4-3 7h3v11" />
    </Icon>
  ),
  table: (
    <Icon>
      <path d="M3 9h18M5 9v10M19 9v10M7 9V5h10v4" />
    </Icon>
  ),
  basket: (
    <Icon>
      <path d="M4 9h16l-1.6 10a2 2 0 0 1-2 1.7H7.6a2 2 0 0 1-2-1.7L4 9z" />
      <path d="M9 9V6a3 3 0 0 1 6 0v3" />
    </Icon>
  ),
  bell: (
    <Icon>
      <path d="M6 16V11a6 6 0 1 1 12 0v5l2 2H4l2-2z" />
      <path d="M10 21h4" />
    </Icon>
  ),
  carry: (
    <Icon>
      <path d="M3 15h18a8 8 0 0 0-16 0" />
      <path d="M2 18h20M12 7V5" />
    </Icon>
  ),
  visit: (
    <Icon>
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="2.8" />
    </Icon>
  ),
  floor: (
    <Icon>
      <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />
    </Icon>
  ),
  chart: (
    <Icon>
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
    </Icon>
  ),
  tag: (
    <Icon>
      <path d="M3 12V4h8l10 10-8 8L3 12z" />
      <circle cx="8" cy="8.5" r="1.2" />
    </Icon>
  ),
  more: (
    <Icon>
      <circle cx="5" cy="12" r="1.2" />
      <circle cx="12" cy="12" r="1.2" />
      <circle cx="19" cy="12" r="1.2" />
    </Icon>
  ),
  calendar: (
    <Icon>
      <path d="M4 6h16v14H4zM4 10h16M8 3v4M16 3v4" />
    </Icon>
  ),
  guests: (
    <Icon>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 20a6 6 0 0 1 12 0M16 5.2a3.2 3.2 0 0 1 0 5.6M18 20a6 6 0 0 0-3-5.2" />
    </Icon>
  ),
  coverage: (
    <Icon>
      <path d="M5 12.5l4.2 4.2L19 7" />
    </Icon>
  ),
  account: (
    <Icon>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" />
    </Icon>
  ),
  plates: (
    <Icon>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="4.2" />
    </Icon>
  ),
};
