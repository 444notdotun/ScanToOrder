// Saving a receipt as a picture, in the browser: the ticket is drawn to a canvas at twice its size, so it is sharp on a phone.

export type SaveResult = 'shared' | 'downloaded' | 'cancelled';

// The export drawing is 540 CSS pixels wide, so the picture is about 1080 pixels wide.
export const PICTURE_WIDTH = 540;
const GROUND = '#10281F';

export async function pictureOf(node: HTMLElement): Promise<Blob> {
  // Loaded only when someone saves a receipt, so it never weighs on the page.
  const { default: html2canvas } = await import('html2canvas');
  if (document.fonts?.ready) await document.fonts.ready;
  const canvas = await html2canvas(node, { scale: 2, backgroundColor: GROUND, logging: false, useCORS: true, width: PICTURE_WIDTH });
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('empty picture'))), 'image/png');
  });
}

// Opens the phone's share sheet where it can take a file (so it can go to Photos or WhatsApp), and downloads the file otherwise.
export async function saveOrShare(blob: Blob, fileName: string, title: string): Promise<SaveResult> {
  const file = new File([blob], fileName, { type: 'image/png' });
  if (typeof navigator !== 'undefined' && typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title });
      return 'shared';
    } catch (error) {
      // Closing the share sheet is not a failure.
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled';
    }
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
  return 'downloaded';
}
