import type { Metadata } from "next";
import Link from "next/link";
import { Download, ShieldCheck, Cpu, HardDrive, AlertTriangle, ArrowRight, ExternalLink, CheckCircle } from "lucide-react";
import { APP_CONFIG } from "@/lib/constants";
import { getLatestRelease } from "@/lib/github";
import { SecurityBadge } from "@/components/SecurityBadge";
import { PlayProtectNote } from "@/components/PlayProtectNote";

export const metadata: Metadata = {
  title: "Download Crunch APK",
  description:
    "Download official signed APK binaries for Crunch on Android. Choose from Universal, arm64-v8a, armeabi-v7a, and x86_64 architectures.",
};

export default async function DownloadPage() {
  const latestRelease = await getLatestRelease();

  return (
    <div className="py-12 md:py-20 radial-bg">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00E5C0]/10 border border-[#00E5C0]/30 text-xs font-semibold text-[#00E5C0]">
            Release {APP_CONFIG.currentVersion}
          </div>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-[#F0F4F8] tracking-tight">
            Download Crunch for Android
          </h1>
          <p className="text-base text-[#8A9BB0]">
            Sideload the authentic, unadulterated media extraction engine directly onto your Android smartphone, tablet, or emulator.
          </p>
        </div>

        {/* Primary Download Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-16">
          {APP_CONFIG.architectures.map((arch) => {
            const downloadUrl = `${APP_CONFIG.downloadBaseUrl}/${arch.filename}`;
            return (
              <div
                key={arch.id}
                className={`p-6 rounded-2xl flex flex-col justify-between transition-all ${
                  arch.recommended
                    ? "bg-[#1A222B] border-2 border-[#00E5C0] shadow-[0_0_35px_rgba(0,229,192,0.15)]"
                    : "bg-[#1A1F27] border border-[#2A3140]"
                }`}
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-base font-bold text-[#F0F4F8]">{arch.name}</span>
                    <span className="text-xs font-mono text-[#00E5C0] px-2 py-0.5 rounded bg-[#12161C] border border-[#2A3140]">
                      {arch.size}
                    </span>
                  </div>

                  <p className="text-xs text-[#8A9BB0] leading-relaxed mb-6">
                    {arch.description}
                  </p>
                </div>

                <div className="space-y-3 pt-4 border-t border-[#2A3140]">
                  <a
                    href={downloadUrl}
                    className={`w-full py-3 px-4 rounded-xl text-xs font-bold inline-flex items-center justify-center gap-2 transition-all ${
                      arch.recommended
                        ? "bg-[#00E5C0] hover:bg-[#00C4A5] text-[#12161C] shadow-[0_0_15px_rgba(0,229,192,0.3)]"
                        : "bg-[#222933] hover:bg-[#2A3342] text-[#F0F4F8] border border-[#2A3140]"
                    }`}
                  >
                    <Download className="w-4 h-4" />
                    Download APK
                  </a>
                  <div className="text-[11px] font-mono text-center text-[#5D6F85] truncate">
                    {arch.filename}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Step-by-Step Installation & Sideload Guide */}
        <div className="mb-16 p-8 rounded-3xl bg-[#1A1F27] border border-[#2A3140]">
          <h2 className="text-xl sm:text-2xl font-bold text-[#F0F4F8] mb-6 flex items-center gap-2.5">
            <CheckCircle className="w-6 h-6 text-[#00E5C0]" />
            Step-by-Step Sideloading Instructions
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            <div className="p-5 rounded-xl bg-[#12161C] border border-[#2A3140] space-y-3">
              <div className="w-8 h-8 rounded-lg bg-[#00E5C0]/10 border border-[#00E5C0]/30 flex items-center justify-center font-bold text-[#00E5C0] text-sm">
                1
              </div>
              <h3 className="text-sm font-bold text-[#F0F4F8]">Download the APK</h3>
              <p className="text-xs text-[#8A9BB0] leading-relaxed">
                Tap the Universal or device-specific APK button above. Your browser will save it to your device Downloads folder.
              </p>
            </div>

            <div className="p-5 rounded-xl bg-[#12161C] border border-[#2A3140] space-y-3">
              <div className="w-8 h-8 rounded-lg bg-[#00E5C0]/10 border border-[#00E5C0]/30 flex items-center justify-center font-bold text-[#00E5C0] text-sm">
                2
              </div>
              <h3 className="text-sm font-bold text-[#F0F4F8]">Allow Unknown Sources</h3>
              <p className="text-xs text-[#8A9BB0] leading-relaxed">
                If prompted by Android, permit your browser or file manager to install unknown apps (Settings → Security → Install unknown apps).
              </p>
            </div>

            <div className="p-5 rounded-xl bg-[#12161C] border border-[#2A3140] space-y-3">
              <div className="w-8 h-8 rounded-lg bg-[#00E5C0]/10 border border-[#00E5C0]/30 flex items-center justify-center font-bold text-[#00E5C0] text-sm">
                3
              </div>
              <h3 className="text-sm font-bold text-[#F0F4F8]">Play Protect Prompt</h3>
              <p className="text-xs text-[#8A9BB0] leading-relaxed">
                If a Play Protect warning dialog appears, tap &ldquo;More details&rdquo; and tap &ldquo;Install anyway&rdquo;.
              </p>
            </div>

            <div className="p-5 rounded-xl bg-[#12161C] border border-[#2A3140] space-y-3">
              <div className="w-8 h-8 rounded-lg bg-[#00E5C0]/10 border border-[#00E5C0]/30 flex items-center justify-center font-bold text-[#00E5C0] text-sm">
                4
              </div>
              <h3 className="text-sm font-bold text-[#F0F4F8]">Start Downloading</h3>
              <p className="text-xs text-[#8A9BB0] leading-relaxed">
                Open Crunch! Paste any URL and enjoy rapid parallel chunk streaming. History is automatically preserved across updates.
              </p>
            </div>
          </div>
        </div>

        {/* Play Protect Note */}
        <div className="mb-16">
          <PlayProtectNote />
        </div>

        {/* Security verification */}
        <SecurityBadge />
      </div>
    </div>
  );
}

