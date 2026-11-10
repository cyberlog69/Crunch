"use client";

import { useState } from "react";
import { Download, Cpu, HardDrive, ExternalLink, HelpCircle, ChevronDown, Check } from "lucide-react";
import { APP_CONFIG } from "@/lib/constants";

export function DownloadCards() {
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [playStoreFaqOpen, setPlayStoreFaqOpen] = useState(false);

  return (
    <section id="download" className="py-20 md:py-32 border-t border-[#2A3140]/80 bg-[#12161C] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00E5C0]/10 border border-[#00E5C0]/30 text-xs font-semibold text-[#00E5C0]">
            Official Binaries
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-[#F0F4F8] tracking-tight">
            Download Crunch for Android
          </h2>
          <p className="text-base text-[#8A9BB0]">
            Select the build tailored for your device architecture, or download the Universal APK if unsure.
          </p>
        </div>

        {/* APK Architecture Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
          {APP_CONFIG.architectures.map((arch) => {
            const downloadUrl = `${APP_CONFIG.downloadBaseUrl}/${arch.filename}`;

            return (
              <div
                key={arch.id}
                className={`relative p-6 rounded-2xl flex flex-col justify-between transition-all duration-200 ${
                  arch.recommended
                    ? "bg-[#1A222B] border-2 border-[#00E5C0] shadow-[0_0_30px_rgba(0,229,192,0.15)] hover:shadow-[0_0_40px_rgba(0,229,192,0.25)]"
                    : "bg-[#1A1F27] border border-[#2A3140] hover:border-[#8A9BB0]/40"
                }`}
              >
                {arch.recommended && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-[#00E5C0] text-[#12161C] text-[11px] font-bold uppercase tracking-wider shadow">
                    Most Compatible
                  </div>
                )}

                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-sm font-mono font-bold text-[#F0F4F8]">{arch.name}</span>
                    <span className="text-xs font-mono text-[#8A9BB0] px-2 py-0.5 rounded bg-[#12161C] border border-[#2A3140]">
                      {arch.size}
                    </span>
                  </div>

                  <p className="text-xs text-[#8A9BB0] leading-relaxed mb-6">
                    {arch.description}
                  </p>
                </div>

                <div className="space-y-2 pt-4 border-t border-[#2A3140]/60">
                  <a
                    href={downloadUrl}
                    className={`w-full py-2.5 px-4 rounded-xl text-xs font-bold inline-flex items-center justify-center gap-2 transition-all ${
                      arch.recommended
                        ? "bg-[#00E5C0] hover:bg-[#00C4A5] text-[#12161C] shadow-[0_0_15px_rgba(0,229,192,0.3)]"
                        : "bg-[#222933] hover:bg-[#2A3342] text-[#F0F4F8] border border-[#2A3140]"
                    }`}
                  >
                    <Download className="w-3.5 h-3.5" />
                    Download APK
                  </a>

                  <div className="text-[11px] text-center text-[#5D6F85] font-mono truncate">
                    {arch.filename}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Collapsible FAQ: Why not on Play Store? */}
        <div className="max-w-3xl mx-auto rounded-2xl bg-[#1A1F27] border border-[#2A3140] overflow-hidden">
          <button
            onClick={() => setPlayStoreFaqOpen(!playStoreFaqOpen)}
            className="w-full p-5 text-left flex items-center justify-between text-sm font-semibold text-[#F0F4F8] hover:text-[#00E5C0] transition-colors"
          >
            <div className="flex items-center gap-2.5">
              <HelpCircle className="w-4 h-4 text-[#00E5C0]" />
              <span>Why is Crunch not published on the Google Play Store?</span>
            </div>
            <ChevronDown
              className={`w-4 h-4 text-[#8A9BB0] transition-transform duration-200 ${
                playStoreFaqOpen ? "rotate-180" : ""
              }`}
            />
          </button>

          {playStoreFaqOpen && (
            <div className="px-5 pb-5 text-xs text-[#8A9BB0] leading-relaxed space-y-2.5 border-t border-[#2A3140]/60 pt-4">
              <p>
                Crunch utilizes the industry-standard <strong className="text-[#F0F4F8]">yt-dlp</strong> stream extraction engine. Google Play Developer Program Policies strictly forbid any application designed to extract or download copyrighted video or audio streams from third-party services.
              </p>
              <p>
                To provide you with unrestricted multi-platform support, full source transparency, and zero ads or trackers, Crunch is published exclusively as an open-source sideload package directly on GitHub.
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

