import type { Metadata, Viewport } from "next";
import { Caveat, Fraunces, Karla, Young_Serif } from "next/font/google";
import "./globals.css";
import Providers from "@/components/Providers";
import { Toaster } from "sonner";

const youngSerif = Young_Serif({ weight: "400", subsets: ["latin"], variable: "--font-young-serif", display: "swap" });
const karla = Karla({ weight: ["400", "500", "700"], subsets: ["latin"], variable: "--font-karla", display: "swap" });
const caveat = Caveat({ weight: "700", subsets: ["latin"], variable: "--font-caveat", display: "swap" });
const fraunces = Fraunces({ weight: "600", subsets: ["latin"], variable: "--font-fraunces", display: "swap" });

export const metadata: Metadata = {
  title: "Plately",
  description: "Pull up a chair. Order and pay from your phone, and a real person brings it over.",
};

export const viewport: Viewport = {
  themeColor: "#183A2E",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`min-h-full bg-bottle antialiased ${youngSerif.variable} ${karla.variable} ${caveat.variable} ${fraunces.variable}`}>
      <body className="min-h-dvh flex flex-col bg-bottle text-stone-900 font-body">
        <Providers>
          <div className="flex-1 flex flex-col">{children}</div>
          {/* Until the staff screens are rebuilt they still report with toasts; the Plately screens never do. */}
          <Toaster richColors position="top-right" />
        </Providers>
      </body>
    </html>
  );
}
