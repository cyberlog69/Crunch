import { Globe2, Split, Film, Palette, EyeOff, Database, ArrowUpRight } from "lucide-react";

const FEATURES = [
  {
    icon: Globe2,
    tag: "yt-dlp Core",
    title: "1,000+ Supported Sites",
    description:
      "Full extraction compatibility across video platforms, social feeds, livestreams, and podcast networks without browser limitations.",
  },
  {
    icon: Split,
    tag: "aria2c Engine",
    title: "Multi-Segment Socket Accelerator",
    description:
      "Splits video payloads into up to 32 parallel chunk streams to bypass mobile carrier and CDN server throttling.",
  },
  {
    icon: Film,
    tag: "Native NDK 7.1.1",
    title: "Bare-Metal FFmpeg Processing",
    description:
      "Zero Java bridge bottlenecks. Remux separate video/audio feeds, embed cover art, and encode MP3, OPUS, or FLAC natively.",
  },
  {
    icon: Database,
    tag: "DownloadHistorySync",
    title: "Resilient History Persistence",
    description:
      "All downloads reside in standard public storage (Downloads/Crunch). An automated scanner re-indexes your media across updates.",
  },
  {
    icon: Palette,
    tag: "Material 3 / You",
    title: "Dynamic System Theming",
    description:
      "Adapts seamlessly to your wallpaper colors with true AMOLED black backgrounds, smooth animations, and clean toggle controls.",
  },
  {
    icon: EyeOff,
    tag: "Strict Privacy",
    title: "Zero Analytics, Zero Tracking",
    description:
      "No Google Analytics, no Firebase Crashlytics, no ad networks. The app never makes a network request unless you give it a URL.",
  },
];

export function FeaturesGrid() {
  return (
    <section id="features" className="py-20 md:py-32 border-t border-[#2A3140]/80 bg-[#12161C] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Heading */}
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-16">
          <h2 className="text-xs font-semibold tracking-widest text-[#00E5C0] uppercase">
            Engineering Highlights
          </h2>
          <p className="text-3xl sm:text-4xl font-extrabold text-[#F0F4F8] tracking-tight">
            Engineered from source for pure speed and device privacy.
          </p>
          <p className="text-base text-[#8A9BB0]">
            Unlike typical browser-wrapper apps, Crunch packages native compiled C/C++ binaries for unmatched throughput.
          </p>
        </div>

        {/* Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {FEATURES.map((feature, idx) => {
            const Icon = feature.icon;
            return (
              <div
                key={idx}
                className="group relative p-7 rounded-2xl bg-[#1A1F27] border border-[#2A3140] hover:border-[#00E5C0]/50 transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_10px_30px_-10px_rgba(0,229,192,0.15)] flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-5">
                    <div className="w-12 h-12 rounded-xl bg-[#222933] group-hover:bg-[#00E5C0]/10 border border-[#2A3140] group-hover:border-[#00E5C0]/30 flex items-center justify-center transition-colors">
                      <Icon className="w-6 h-6 text-[#00E5C0]" />
                    </div>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-[#12161C] text-[#8A9BB0] border border-[#2A3140]">
                      {feature.tag}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-[#F0F4F8] mb-2 group-hover:text-[#00E5C0] transition-colors">
                    {feature.title}
                  </h3>

                  <p className="text-sm text-[#8A9BB0] leading-relaxed">
                    {feature.description}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

