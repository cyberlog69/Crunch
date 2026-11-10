import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { Footer } from "@/components/Footer";
import { APP_CONFIG } from "@/lib/constants";

export const viewport: Viewport = {
  themeColor: "#12161C",
  colorScheme: "dark",
};

export const metadata: Metadata = {
  metadataBase: new URL("https://crunch.kappi.com"),
  title: {
    default: "Crunch — High-Speed Media & Stream Engine for Android",
    template: "%s | Crunch",
  },
  description: APP_CONFIG.description,
  keywords: [
    "Crunch",
    "Crunch APK",
    "Android media downloader",
    "yt-dlp Android",
    "FFmpeg Android",
    "aria2c Android",
    "video downloader",
    "audio extraction",
    "open source downloader",
    "zero telemetry",
  ],
  authors: [{ name: "Kappi / cyberlog69" }],
  creator: "Kappi",
  icons: {
    icon: "/icon.png",
    apple: "/icon.png",
  },
  openGraph: {
    title: "Crunch — High-Speed Media & Stream Engine for Android",
    description: APP_CONFIG.description,
    url: "https://crunch.kappi.com",
    siteName: "Crunch",
    images: [
      {
        url: "/icon.png",
        width: 512,
        height: 512,
        alt: "Crunch App Icon",
      },
    ],
    locale: "en_US",
    type: "website",
  },
  twitter: {
    card: "summary",
    title: "Crunch — High-Speed Media & Stream Engine",
    description: APP_CONFIG.description,
    images: ["/icon.png"],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark h-full antialiased">
      <body className="min-h-full flex flex-col bg-[#12161C] text-[#F0F4F8] font-sans">
        <Navbar />
        <main className="flex-1">{children}</main>
        <Footer />
      </body>
    </html>
  );
}
