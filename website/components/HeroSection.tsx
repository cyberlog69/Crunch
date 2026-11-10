import Link from "next/link";
import Image from "next/image";
import { Download, ShieldCheck, Zap, Layers, Sparkles, ArrowRight } from "lucide-react";
import { GithubIcon } from "@/components/GithubIcon";
import { APP_CONFIG } from "@/lib/constants";

export function HeroSection() {
  return (
    <section className="relative pt-12 pb-20 md:pt-20 md:pb-32 overflow-hidden radial-bg">
      {/* Decorative background grid */}
      <div className="absolute inset-0 grid-pattern pointer-events-none opacity-40" />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          {/* Left Column: Copy & Actions */}
          <div className="lg:col-span-7 space-y-6 text-center lg:text-left">
            {/* Version badge */}
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#1A1F27] border border-[#00E5C0]/30 text-xs font-medium text-[#00E5C0] shadow-[0_0_15px_rgba(0,229,192,0.1)]">
              <Sparkles className="w-3.5 h-3.5 text-[#00E5C0]" />
              <span>Crunch {APP_CONFIG.currentVersion} is live · Native NDK Engine</span>
            </div>

            {/* Main Headline */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-[#F0F4F8] leading-[1.15]">
              Stream & Media Engine{" "}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#00E5C0] via-[#38EF7D] to-[#00C4A5]">
                Built for Speed.
              </span>
            </h1>

            {/* Subheading */}
            <p className="text-lg sm:text-xl text-[#8A9BB0] max-w-2xl mx-auto lg:mx-0 leading-relaxed font-normal">
              Directly extract high-resolution video and pristine audio from over 1,000 platforms.
              Supercharged with <strong className="text-[#F0F4F8]">aria2c chunk splitting</strong>,{" "}
              <strong className="text-[#F0F4F8]">native FFmpeg remuxing</strong>, and zero telemetry.
            </p>

            {/* Primary Action Buttons */}
            <div className="pt-2 flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4">
              <Link
                href="/download"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-8 py-4 rounded-xl bg-[#00E5C0] hover:bg-[#00C4A5] text-[#12161C] font-bold text-base transition-all shadow-[0_0_25px_rgba(0,229,192,0.35)] hover:shadow-[0_0_35px_rgba(0,229,192,0.55)] hover:-translate-y-0.5"
              >
                <Download className="w-5 h-5 text-[#12161C]" />
                <span>Download Universal APK</span>
                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-[#12161C]/20 text-[#12161C]">
                  {APP_CONFIG.currentVersion}
                </span>
              </Link>

              <a
                href={APP_CONFIG.githubRepo}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-6 py-4 rounded-xl border border-[#2A3140] bg-[#1A1F27] hover:bg-[#222933] text-[#F0F4F8] font-semibold text-base transition-all hover:border-[#8A9BB0]/40"
              >
                <GithubIcon className="w-5 h-5" />
                <span>Source Code</span>
              </a>
            </div>

            {/* Architecture picker hint */}
            <div className="flex items-center justify-center lg:justify-start gap-2 text-xs text-[#8A9BB0] pt-1">
              <span>Looking for device-specific builds?</span>
              <Link
                href="/download"
                className="text-[#00E5C0] hover:underline inline-flex items-center gap-1 font-medium"
              >
                arm64, armeabi, x86_64 <ArrowRight className="w-3 h-3" />
              </Link>
            </div>

            {/* Trust highlights */}
            <div className="pt-6 grid grid-cols-3 gap-4 border-t border-[#2A3140]/60 text-left max-w-lg mx-auto lg:mx-0">
              <div className="space-y-1">
                <div className="text-2xl font-bold text-[#F0F4F8]">1000+</div>
                <div className="text-xs text-[#8A9BB0]">Sites Supported</div>
              </div>
              <div className="space-y-1">
                <div className="text-2xl font-bold text-[#00E5C0]">32x</div>
                <div className="text-xs text-[#8A9BB0]">Parallel Sockets</div>
              </div>
              <div className="space-y-1">
                <div className="text-2xl font-bold text-[#F0F4F8]">0</div>
                <div className="text-xs text-[#8A9BB0]">Trackers or Ads</div>
              </div>
            </div>
          </div>

          {/* Right Column: App Mockup & Floating Badges */}
          <div className="lg:col-span-5 relative flex justify-center items-center">
            {/* Glow effect behind phone */}
            <div className="absolute w-72 h-72 sm:w-96 sm:h-96 rounded-full bg-[#00E5C0]/15 blur-3xl -z-10" />

            {/* Phone Frame Mockup */}
            <div className="relative w-[280px] sm:w-[310px] rounded-[38px] p-2.5 bg-gradient-to-b from-[#2A3140] via-[#1A1F27] to-[#12161C] shadow-2xl border border-[#2A3140]">
              <div className="relative rounded-[30px] overflow-hidden bg-[#12161C] border border-[#2A3140]/80 aspect-[9/19.5]">
                <Image
                  src="/screenshots/downloads.jpg"
                  alt="Crunch App Interface"
                  fill
                  className="object-cover object-top"
                  sizes="(max-width: 640px) 280px, 310px"
                  priority
                />
              </div>

              {/* Floating Pill 1: Parallel Engine */}
              <div className="absolute -left-6 sm:-left-10 top-1/4 px-3.5 py-2 rounded-xl bg-[#1A1F27]/95 border border-[#2A3140] backdrop-blur-md shadow-xl flex items-center gap-2.5">
                <div className="p-1.5 rounded-lg bg-[#00E5C0]/10 text-[#00E5C0]">
                  <Zap className="w-4 h-4" />
                </div>
                <div className="text-left">
                  <div className="text-xs font-bold text-[#F0F4F8]">aria2c 1.37.0</div>
                  <div className="text-[10px] text-[#8A9BB0]">Multi-segment engine</div>
                </div>
              </div>

              {/* Floating Pill 2: MediaStore Persistence */}
              <div className="absolute -right-6 sm:-right-8 bottom-1/4 px-3.5 py-2 rounded-xl bg-[#1A1F27]/95 border border-[#2A3140] backdrop-blur-md shadow-xl flex items-center gap-2.5">
                <div className="p-1.5 rounded-lg bg-[#38EF7D]/10 text-[#38EF7D]">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <div className="text-left">
                  <div className="text-xs font-bold text-[#F0F4F8]">HistorySync</div>
                  <div className="text-[10px] text-[#8A9BB0]">Survives reinstalls</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
