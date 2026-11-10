"use client";

import { useState } from "react";
import Image from "next/image";
import { AlertTriangle, ShieldCheck, CheckCircle, ChevronRight, X } from "lucide-react";

export function PlayProtectNote() {
  const [showScreenshot, setShowScreenshot] = useState(false);

  return (
    <section className="py-12 bg-[#161B22] border-t border-[#2A3140]/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="p-6 md:p-8 rounded-2xl bg-gradient-to-r from-[#211B14] via-[#1A1F27] to-[#1A1F27] border border-[#E5A000]/40 relative overflow-hidden">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="space-y-3 max-w-3xl">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#E5A000]/10 border border-[#E5A000]/30 text-xs font-semibold text-[#FFB726]">
                <AlertTriangle className="w-3.5 h-3.5 text-[#FFB726]" />
                <span>Sideloading Note: Google Play Protect Dialog</span>
              </div>

              <h3 className="text-lg sm:text-xl font-bold text-[#F0F4F8]">
                Seeing a &ldquo;Harmful app blocked&rdquo; or &ldquo;Unrecognized Developer&rdquo; prompt?
              </h3>

              <p className="text-xs sm:text-sm text-[#8A9BB0] leading-relaxed">
                Because Crunch is distributed directly via GitHub and does not include Google telemetry or advertising SDKs, Play Protect cannot scan it against its cloud-indexed developer database. This is a standard warning for sideloaded open-source tools.
              </p>

              <div className="flex flex-wrap items-center gap-4 text-xs font-medium text-[#F0F4F8] pt-1">
                <div className="flex items-center gap-1.5">
                  <CheckCircle className="w-4 h-4 text-[#00E5C0]" />
                  <span>Tap <strong>&ldquo;More details&rdquo;</strong></span>
                </div>
                <ChevronRight className="w-3.5 h-3.5 text-[#8A9BB0]" />
                <div className="flex items-center gap-1.5">
                  <CheckCircle className="w-4 h-4 text-[#00E5C0]" />
                  <span>Select <strong>&ldquo;Install anyway&rdquo;</strong></span>
                </div>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row items-center gap-3 w-full md:w-auto">
              <button
                onClick={() => setShowScreenshot(true)}
                className="w-full sm:w-auto px-4 py-2.5 rounded-xl border border-[#2A3140] bg-[#12161C] hover:bg-[#222933] text-xs font-semibold text-[#F0F4F8] transition-colors"
              >
                View Dialog Preview
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Dialog Preview Modal */}
      {showScreenshot && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="relative max-w-md w-full bg-[#1A1F27] border border-[#2A3140] rounded-2xl p-6 shadow-2xl">
            <button
              onClick={() => setShowScreenshot(false)}
              className="absolute top-4 right-4 p-1.5 rounded-lg text-[#8A9BB0] hover:text-[#F0F4F8] bg-[#12161C] border border-[#2A3140]"
              aria-label="Close dialog"
            >
              <X className="w-4 h-4" />
            </button>

            <h4 className="text-base font-bold text-[#F0F4F8] mb-2 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-[#00E5C0]" />
              Safe Installation Guide
            </h4>
            <p className="text-xs text-[#8A9BB0] mb-4">
              When prompted by Android, tap &ldquo;Install anyway&rdquo;. You can verify that your APK signature matches our official RSA-2048 fingerprint at any time.
            </p>

            <div className="relative w-full h-[400px] rounded-xl overflow-hidden border border-[#2A3140] bg-[#12161C]">
              <Image
                src="/screenshots/playprotect.jpg"
                alt="Play Protect Sideload Prompt"
                fill
                className="object-contain"
              />
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

