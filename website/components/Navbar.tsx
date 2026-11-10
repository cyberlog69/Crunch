"use client";

import Link from "next/link";
import Image from "next/image";
import { useState } from "react";
import { Download, Menu, X, Shield, Sparkles } from "lucide-react";
import { GithubIcon } from "@/components/GithubIcon";
import { APP_CONFIG } from "@/lib/constants";

export function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-[#2A3140]/80 bg-[#12161C]/85 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-3 group">
            <div className="relative w-9 h-9 rounded-xl overflow-hidden border border-[#2A3140] group-hover:border-[#00E5C0] transition-colors">
              <Image
                src="/icon.png"
                alt="Crunch Icon"
                fill
                className="object-cover"
                priority
              />
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg tracking-tight text-[#F0F4F8] group-hover:text-[#00E5C0] transition-colors">
                  {APP_CONFIG.name}
                </span>
                <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full bg-[#00E5C0]/10 text-[#00E5C0] border border-[#00E5C0]/20">
                  {APP_CONFIG.currentVersion}
                </span>
              </div>
            </div>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-[#8A9BB0]">
            <Link
              href="/#features"
              className="hover:text-[#00E5C0] transition-colors"
            >
              Features
            </Link>
            <Link
              href="/#how-it-works"
              className="hover:text-[#00E5C0] transition-colors"
            >
              How It Works
            </Link>
            <Link
              href="/download"
              className="hover:text-[#00E5C0] transition-colors flex items-center gap-1.5"
            >
              <Download className="w-3.5 h-3.5" />
              Download
            </Link>
            <Link
              href="/faq"
              className="hover:text-[#00E5C0] transition-colors"
            >
              FAQ
            </Link>
            <Link
              href="/changelog"
              className="hover:text-[#00E5C0] transition-colors"
            >
              Changelog
            </Link>
          </nav>

          {/* Action Buttons */}
          <div className="hidden sm:flex items-center gap-3">
            <a
              href={APP_CONFIG.githubRepo}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-[#2A3140] bg-[#1A1F27] hover:bg-[#222933] hover:text-[#F0F4F8] text-xs font-medium text-[#8A9BB0] transition-colors"
            >
              <GithubIcon className="w-4 h-4 text-[#F0F4F8]" />
              <span>GitHub</span>
            </a>
            <Link
              href="/download"
              className="inline-flex items-center gap-2 px-4 py-1.5 rounded-lg bg-[#00E5C0] hover:bg-[#00C4A5] text-[#12161C] font-semibold text-xs tracking-wide transition-all shadow-[0_0_15px_rgba(0,229,192,0.3)] hover:shadow-[0_0_20px_rgba(0,229,192,0.5)]"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Get APK</span>
            </Link>
          </div>

          {/* Mobile menu button */}
          <div className="flex md:hidden">
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 rounded-lg text-[#8A9BB0] hover:text-[#F0F4F8] hover:bg-[#1A1F27]"
              aria-label="Toggle menu"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden border-b border-[#2A3140] bg-[#161B23] px-4 pt-2 pb-6 space-y-3">
          <Link
            href="/#features"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-md text-base font-medium text-[#8A9BB0] hover:text-[#00E5C0] hover:bg-[#1A1F27]"
          >
            Features
          </Link>
          <Link
            href="/#how-it-works"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-md text-base font-medium text-[#8A9BB0] hover:text-[#00E5C0] hover:bg-[#1A1F27]"
          >
            How It Works
          </Link>
          <Link
            href="/download"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-md text-base font-medium text-[#8A9BB0] hover:text-[#00E5C0] hover:bg-[#1A1F27]"
          >
            Download APK
          </Link>
          <Link
            href="/faq"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-md text-base font-medium text-[#8A9BB0] hover:text-[#00E5C0] hover:bg-[#1A1F27]"
          >
            FAQ
          </Link>
          <Link
            href="/changelog"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-md text-base font-medium text-[#8A9BB0] hover:text-[#00E5C0] hover:bg-[#1A1F27]"
          >
            Changelog
          </Link>
          <div className="pt-2 flex flex-col gap-2">
            <Link
              href="/download"
              onClick={() => setMobileMenuOpen(false)}
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-[#00E5C0] text-[#12161C] font-semibold text-sm"
            >
              <Download className="w-4 h-4" />
              Download Crunch APK
            </Link>
            <a
              href={APP_CONFIG.githubRepo}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg border border-[#2A3140] bg-[#1A1F27] text-[#F0F4F8] font-medium text-sm"
            >
              <GithubIcon className="w-4 h-4" />
              GitHub Repository
            </a>
          </div>
        </div>
      )}
    </header>
  );
}
