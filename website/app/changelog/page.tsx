import type { Metadata } from "next";
import { GitCommit, Download, Calendar, ExternalLink, Sparkles, Tag } from "lucide-react";
import { getLatestReleases, formatBytes } from "@/lib/github";
import { APP_CONFIG } from "@/lib/constants";

export const metadata: Metadata = {
  title: "Changelog & Releases",
  description:
    "See what is new in every version of Crunch. Release notes, APK downloads, performance updates, and bug fixes.",
};

export default async function ChangelogPage() {
  const releases = await getLatestReleases();

  return (
    <div className="py-12 md:py-20 radial-bg">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00E5C0]/10 border border-[#00E5C0]/30 text-xs font-semibold text-[#00E5C0]">
            Version History
          </div>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-[#F0F4F8] tracking-tight">
            Changelog & Releases
          </h1>
          <p className="text-base text-[#8A9BB0]">
            Track every release, component update, and security enhancement shipped to Crunch.
          </p>
        </div>

        {/* Releases Timeline */}
        <div className="space-y-10">
          {releases.map((rel, idx) => {
            const formattedDate = new Date(rel.published_at).toLocaleDateString("en-US", {
              year: "numeric",
              month: "long",
              day: "numeric",
            });

            return (
              <div
                key={rel.id || idx}
                className="p-8 rounded-3xl bg-[#1A1F27] border border-[#2A3140] relative overflow-hidden space-y-6"
              >
                {/* Header row */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-[#2A3140]">
                  <div className="flex items-center gap-3">
                    <span className="text-xl sm:text-2xl font-black text-[#F0F4F8] font-mono">
                      {rel.tag_name}
                    </span>
                    {idx === 0 && (
                      <span className="px-2.5 py-0.5 rounded-full bg-[#00E5C0]/15 text-[#00E5C0] text-xs font-bold border border-[#00E5C0]/30">
                        Latest
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-4 text-xs text-[#8A9BB0]">
                    <div className="flex items-center gap-1.5">
                      <Calendar className="w-3.5 h-3.5 text-[#00E5C0]" />
                      <span>{formattedDate}</span>
                    </div>
                    <a
                      href={rel.html_url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="hover:text-[#00E5C0] transition-colors flex items-center gap-1"
                    >
                      <span>GitHub</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  </div>
                </div>

                {/* Release Name */}
                <h2 className="text-lg font-bold text-[#F0F4F8]">
                  {rel.name || `Crunch ${rel.tag_name}`}
                </h2>

                {/* Body / Markdown Notes */}
                <div className="text-sm text-[#8A9BB0] leading-relaxed whitespace-pre-line bg-[#12161C] p-6 rounded-2xl border border-[#2A3140] font-sans">
                  {rel.body}
                </div>

                {/* Assets / APK downloads */}
                {rel.assets && rel.assets.length > 0 && (
                  <div className="space-y-3 pt-2">
                    <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F0F4F8]">
                      Download Assets
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {rel.assets.map((asset) => (
                        <a
                          key={asset.id}
                          href={asset.browser_download_url}
                          className="p-3.5 rounded-xl bg-[#222933] hover:bg-[#2A3342] border border-[#2A3140] hover:border-[#00E5C0]/40 flex items-center justify-between text-xs text-[#F0F4F8] transition-colors group"
                        >
                          <div className="flex items-center gap-2.5 truncate">
                            <Download className="w-4 h-4 text-[#00E5C0] shrink-0" />
                            <span className="truncate font-mono">{asset.name}</span>
                          </div>
                          <span className="text-[11px] text-[#8A9BB0] shrink-0 ml-2 font-mono">
                            {formatBytes(asset.size)}
                          </span>
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* View all on GitHub */}
        <div className="text-center pt-12">
          <a
            href={APP_CONFIG.githubReleasesUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl border border-[#2A3140] bg-[#1A1F27] hover:bg-[#222933] text-xs font-semibold text-[#F0F4F8] transition-colors"
          >
            <span>View All GitHub Releases</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </div>
  );
}

