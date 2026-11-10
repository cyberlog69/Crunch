import type { Metadata } from "next";
import Link from "next/link";
import { HelpCircle, ChevronDown, Shield, Download } from "lucide-react";
import { GithubIcon } from "@/components/GithubIcon";
import { APP_CONFIG } from "@/lib/constants";

export const metadata: Metadata = {
  title: "Frequently Asked Questions",
  description:
    "Everything you need to know about Crunch: Google Play Protect warnings, privacy model, audio/video formats, and storage persistence.",
};

const FAQS = [
  {
    q: "Why isn't Crunch on the Google Play Store?",
    a: "Google Play Developer Policies strictly prohibit apps that allow users to download or extract copyrighted content from YouTube and other streaming platforms. Because Crunch uses yt-dlp to support 1000+ websites, publishing it on Google Play is not permitted. Distributing directly via GitHub allows us to keep the app completely free, unrestricted, open source, and free of advertising SDKs.",
  },
  {
    q: "Is the Google Play Protect warning dangerous?",
    a: "No. Android's Play Protect system flags apps that are not distributed through the Google Play Store or that lack corporate tracking telemetry as 'unrecognized' or 'potentially harmful'. Crunch is 100% open-source software built from public code. You can verify the official RSA-2048 signing certificate (SHA-256 digest) against our published security policy to confirm the binary has not been modified.",
  },
  {
    q: "Does Crunch collect any telemetry, logs, or analytics?",
    a: "None whatsoever. There are zero analytics trackers, zero advertising SDKs, and zero crash-reporting services embedded in Crunch. The app only contacts external servers when resolving a media URL that you have provided or when checking for app/engine updates.",
  },
  {
    q: "Will my download history disappear when I install an update?",
    a: "No! Starting in v1.0.7, Crunch features an integrated DownloadHistorySync module. All downloaded media files are written to public storage (Downloads/Crunch). Whenever you install an update or even reinstall the app, Crunch scans this folder and re-indexes your media library automatically.",
  },
  {
    q: "Which video and audio formats are supported?",
    a: "Crunch supports MP4 and MKV video containers with resolutions up to 4K 60fps and HDR, plus MP3, OPUS, FLAC, and M4A audio containers. Because native NDK FFmpeg 7.1.1 is included, audio extraction, remuxing, and thumbnail cover-art embedding happen locally on your device with zero transcoding quality loss.",
  },
  {
    q: "What Android permissions does Crunch require?",
    a: "Crunch follows the principle of least privilege: INTERNET (to fetch streams), FOREGROUND_SERVICE & DATA_SYNC (to keep downloads running smoothly when the screen turns off), and POST_NOTIFICATIONS (for real-time download progress notifications). Crunch does not request root access, contacts, location, or broad storage access (MANAGE_EXTERNAL_STORAGE).",
  },
  {
    q: "How does aria2c make downloads faster?",
    a: "Many content delivery networks (CDNs) throttle individual HTTP connection download speeds. aria2c splits the video file into up to 32 parallel socket connections, retrieving multiple segments simultaneously and merging them in memory. This can accelerate download speeds up to 10× compared to single-threaded downloaders.",
  },
  {
    q: "How do I update Crunch when a new version is released?",
    a: "Crunch includes an in-app updater check in Settings → Updates & Components. When an update is detected, it directs you to the official GitHub Release page to download the latest APK. Because all versions from v1.0.7 onwards share the exact same release keystore, updates install in-place without needing to uninstall previous builds.",
  },
];

export default function FAQPage() {
  return (
    <div className="py-12 md:py-20 radial-bg">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00E5C0]/10 border border-[#00E5C0]/30 text-xs font-semibold text-[#00E5C0]">
            Help & Knowledge Base
          </div>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-[#F0F4F8] tracking-tight">
            Frequently Asked Questions
          </h1>
          <p className="text-base text-[#8A9BB0]">
            Find answers regarding security, Play Protect, formats, and architecture.
          </p>
        </div>

        {/* FAQ list */}
        <div className="space-y-4 mb-16">
          {FAQS.map((faq, idx) => (
            <div
              key={idx}
              className="p-6 rounded-2xl bg-[#1A1F27] border border-[#2A3140] space-y-3"
            >
              <h2 className="text-base sm:text-lg font-bold text-[#F0F4F8] flex items-start gap-3">
                <HelpCircle className="w-5 h-5 text-[#00E5C0] shrink-0 mt-0.5" />
                <span>{faq.q}</span>
              </h2>
              <p className="text-sm text-[#8A9BB0] leading-relaxed pl-8">
                {faq.a}
              </p>
            </div>
          ))}
        </div>

        {/* Still have questions */}
        <div className="p-8 rounded-3xl bg-[#161B22] border border-[#2A3140] text-center space-y-4">
          <h3 className="text-xl font-bold text-[#F0F4F8]">Have another question?</h3>
          <p className="text-sm text-[#8A9BB0] max-w-lg mx-auto">
            Crunch is fully open source. You can review the code, check our security policy, or open a discussion on GitHub.
          </p>
          <div className="pt-2 flex flex-wrap justify-center gap-4">
            <a
              href={`${APP_CONFIG.githubRepo}/issues`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-[#2A3140] bg-[#1A1F27] hover:bg-[#222933] text-xs font-semibold text-[#F0F4F8] transition-colors"
            >
              <GithubIcon className="w-4 h-4" />
              Open an Issue
            </a>
            <Link
              href="/download"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#00E5C0] hover:bg-[#00C4A5] text-[#12161C] text-xs font-bold transition-all"
            >
              <Download className="w-4 h-4" />
              Download Crunch
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
