import Link from "next/link";
import Image from "next/image";
import { Shield, Terminal, FileCode2 } from "lucide-react";
import { GithubIcon } from "@/components/GithubIcon";
import { APP_CONFIG } from "@/lib/constants";

export function Footer() {
  return (
    <footer className="border-t border-[#2A3140] bg-[#0E1217] text-[#8A9BB0] py-12 px-4 sm:px-6 lg:px-8 mt-auto">
      <div className="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-4 gap-8 mb-12">
        {/* Brand */}
        <div className="md:col-span-2 space-y-4">
          <div className="flex items-center gap-3">
            <div className="relative w-8 h-8 rounded-lg overflow-hidden border border-[#2A3140]">
              <Image src="/icon.png" alt="Crunch Logo" fill className="object-cover" />
            </div>
            <span className="font-bold text-lg text-[#F0F4F8]">{APP_CONFIG.name}</span>
          </div>
          <p className="text-sm max-w-sm text-[#8A9BB0] leading-relaxed">
            {APP_CONFIG.tagline}. High-performance media extraction for Android powered by yt-dlp, FFmpeg NDK, and aria2c.
          </p>
          <div className="flex items-center gap-3 pt-2">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#00E5C0]/10 text-[#00E5C0] text-xs font-medium border border-[#00E5C0]/20">
              <Shield className="w-3.5 h-3.5" />
              100% Telemetry Free
            </span>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#1A1F27] text-[#8A9BB0] text-xs font-medium border border-[#2A3140]">
              <Terminal className="w-3.5 h-3.5" />
              Open Source
            </span>
          </div>
        </div>

        {/* Quick Links */}
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wider text-[#F0F4F8] mb-4">
            Navigation
          </h4>
          <ul className="space-y-2.5 text-sm">
            <li>
              <Link href="/#features" className="hover:text-[#00E5C0] transition-colors">
                Key Features
              </Link>
            </li>
            <li>
              <Link href="/#how-it-works" className="hover:text-[#00E5C0] transition-colors">
                How It Works
              </Link>
            </li>
            <li>
              <Link href="/download" className="hover:text-[#00E5C0] transition-colors">
                Download APKs
              </Link>
            </li>
            <li>
              <Link href="/faq" className="hover:text-[#00E5C0] transition-colors">
                Frequently Asked Questions
              </Link>
            </li>
            <li>
              <Link href="/changelog" className="hover:text-[#00E5C0] transition-colors">
                Changelog & Releases
              </Link>
            </li>
          </ul>
        </div>

        {/* Security & GitHub */}
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wider text-[#F0F4F8] mb-4">
            Transparency
          </h4>
          <ul className="space-y-2.5 text-sm">
            <li>
              <a
                href={APP_CONFIG.githubRepo}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-[#00E5C0] transition-colors flex items-center gap-1.5"
              >
                <GithubIcon className="w-4 h-4" />
                GitHub Repository
              </a>
            </li>
            <li>
              <a
                href={APP_CONFIG.securityUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-[#00E5C0] transition-colors flex items-center gap-1.5"
              >
                <Shield className="w-4 h-4" />
                Security & Signing Policy
              </a>
            </li>
            <li>
              <a
                href={`${APP_CONFIG.githubRepo}/issues`}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-[#00E5C0] transition-colors"
              >
                Report an Issue
              </a>
            </li>
            <li>
              <a
                href={`${APP_CONFIG.githubRepo}/blob/main/LICENSE`}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-[#00E5C0] transition-colors flex items-center gap-1.5"
              >
                <FileCode2 className="w-4 h-4" />
                GNU General Public License
              </a>
            </li>
          </ul>
        </div>
      </div>

      <div className="max-w-7xl mx-auto pt-8 border-t border-[#1F2633] flex flex-col sm:flex-row items-center justify-between text-xs text-[#5D6F85] gap-4">
        <div>
          © 2026 {APP_CONFIG.name}. Built with freedom, privacy, and speed in mind.
        </div>
        <div className="flex items-center gap-2">
          <span>Signed with RSA-2048</span>
          <span>•</span>
          <span className="font-mono text-[11px] text-[#8A9BB0]">v1.0.7 (Code 8)</span>
        </div>
      </div>
    </footer>
  );
}
