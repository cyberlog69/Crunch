import { Link2, Sliders, CheckCircle2, ArrowRight } from "lucide-react";

const STEPS = [
  {
    step: "01",
    icon: Link2,
    title: "Paste URL or Share",
    description:
      "Copy any media link from YouTube, Instagram, X (Twitter), TikTok, or 1000+ other sources, or use Android's native 'Share to Crunch' intent.",
  },
  {
    step: "02",
    icon: Sliders,
    title: "Select Format & Quality",
    description:
      "Choose your preferred video resolution (up to 4K 60fps), audio container (MP3, OPUS, FLAC, M4A), thumbnail embedding, or custom tags.",
  },
  {
    step: "03",
    icon: CheckCircle2,
    title: "Ultra-Fast Offline Save",
    description:
      "aria2c accelerates chunk retrieval while native FFmpeg muxes the stream directly into your public Downloads folder, instantly visible in gallery apps.",
  },
];

export function HowItWorks() {
  return (
    <section id="how-it-works" className="py-20 md:py-32 border-t border-[#2A3140]/80 bg-[#161B22] relative overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-16 space-y-4">
          <h2 className="text-xs font-semibold tracking-widest text-[#00E5C0] uppercase">
            Simple Workflow
          </h2>
          <p className="text-3xl sm:text-4xl font-extrabold text-[#F0F4F8] tracking-tight">
            How Crunch Works
          </p>
          <p className="text-sm text-[#8A9BB0]">
            No complex terminal commands or root required. Just pure media extraction in three quick steps.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
          {STEPS.map((s, idx) => {
            const Icon = s.icon;
            return (
              <div
                key={idx}
                className="relative p-8 rounded-2xl bg-[#1A1F27] border border-[#2A3140] flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-6">
                    <span className="text-4xl font-black text-[#2A3140] font-mono">
                      {s.step}
                    </span>
                    <div className="w-10 h-10 rounded-xl bg-[#00E5C0]/10 border border-[#00E5C0]/30 flex items-center justify-center">
                      <Icon className="w-5 h-5 text-[#00E5C0]" />
                    </div>
                  </div>

                  <h3 className="text-lg font-bold text-[#F0F4F8] mb-3">
                    {s.title}
                  </h3>

                  <p className="text-sm text-[#8A9BB0] leading-relaxed">
                    {s.description}
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

