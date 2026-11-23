'use client';

import { useState } from 'react';

// Copies a value to the clipboard and says so in plain words. An older browser gets the old way of copying.
export default function CopyButton({ value, label = 'Copy', copiedLabel = 'Copied ✓', className = '' }: { value: string; label?: string; copiedLabel?: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      const box = document.createElement('textarea');
      box.value = value;
      box.setAttribute('readonly', '');
      box.style.position = 'fixed';
      box.style.opacity = '0';
      document.body.appendChild(box);
      box.select();
      try {
        document.execCommand('copy');
      } catch {
        // Nothing more to try: the person can still read the value and type it.
      }
      box.remove();
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <>
      <button
        type="button"
        onClick={() => void copy()}
        className={`min-h-[44px] rounded-full border-[1.5px] border-bottle px-4 text-[15px] font-bold text-bottle hover:bg-bottle/5 ${className}`}
      >
        {copied ? copiedLabel : label}
      </button>
      <span role="status" className="sr-only">
        {copied ? 'Copied' : ''}
      </span>
    </>
  );
}
