import type { Metadata } from "next";
import "./globals.css";
import Providers from "@/components/Providers";

export const metadata: Metadata = {
  title: "Scan to Order - Restaurant Management & Ordering",
  description: "Seamless QR table ordering, kitchen processing, and waiter coordination.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col bg-bg-warm text-stone-900">
        <Providers>
          <div className="flex-1 flex flex-col">{children}</div>
        </Providers>
      </body>
    </html>
  );
}
