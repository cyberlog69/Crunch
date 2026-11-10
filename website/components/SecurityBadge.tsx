"use client";

import { useState } from "react";
import { Shield, Copy, Check, Terminal, ExternalLink } from "lucide-react";
import { APP_CONFIG } from "@/lib/constants";

export function SecurityBadge() {
  const [copiedFingerprint, setCopiedFingerprint] = useState(false);
  const [copiedCommand, setCopiedCommand] = useState(false);

  const copyFingerprint = () => {
    navigator.clipboard.writeText(APP_CONFIG.signingCert.sha256);
    setCopiedFingerprint(true);
    setTimeout(() => setCopiedFingerprint(false), 2000);
  };

  const copyCommand = () => {
    navigator.clipboard.writeText("apksigner verify --print-certs crunch-v1.0.7-universal-release.apk");
    setCopiedCommand(true);
    setTimeout(() => setCopiedCommand(false), 2000);
  };

  return (
    <section className="py-20 md:py-32 border-t border-[#2A3140]/80 bg-[#12161C] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-14 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#00E5C0]/10 border border-[#00E5C0]/30 text-xs font-semibold text-[#00E5C0]">
            <Shield className="w-3.5 h-3.5 text-[#00E5C0]" />
            Cryptographic Integrity
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-[#F0F4F8] tracking-tight">
            Verify Your Download
          </h2>
          <p className="text-sm text-[#8A9BB0]">
            Every official Crunch release is signed with a permanent offline release keystore.
            Verify that your APK was authored directly by our repository.
          </p>
        </div>

        <div className="max-w-4xl mx-auto space-y-6">
          {/* Certificate SHA-256 Box */}
          <div className="p-6 rounded-2xl bg-[#1A1F27] border border-[#2A3140] space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <span className="text-xs font-mono font-bold uppercase tracking-wider text-[#00E5C0]">
                Official Signing Certificate SHA-256 Digest
              </span>
              <span className="text-[11px] text-[#8A9BB0] font-mono">
                {APP_CONFIG.signingCert.owner}
              </span>
            </div>

            <div className="relative flex items-center justify-between gap-4 p-4 rounded-xl bg-[#12161C] border border-[#2A3140]">
              <code className="text-xs sm:text-sm font-mono text-[#F0F4F8] break-all select-all">
                {APP_CONFIG.signingCert.sha256}
              </code>
              <button
                onClick={copyFingerprint}
                className="shrink-0 p-2 rounded-lg bg-[#1A1F27] hover:bg-[#222933] text-[#8A9BB0] hover:text-[#00E5C0] border border-[#2A3140] transition-colors"
                title="Copy SHA-256 Fingerprint"
              >
                {copiedFingerprint ? (
                  <Check className="w-4 h-4 text-[#00E5C0]" />
                ) : (
                  <Copy className="w-4 h-4" />
                )}
              </button>
            </div>

            <div className="flex flex-wrap items-center justify-between text-xs text-[#8A9BB0] pt-1">
              <span>Algorithm: {APP_CONFIG.signingCert.algorithm}</span>
              <span>Validity: {APP_CONFIG.signingCert.validity}</span>
            </div>
          </div>

          {/* Verification Command Snippet */}
          <div className="p-6 rounded-2xl bg-[#1A1F27] border border-[#2A3140] space-y-4">
            <div className="flex items-center gap-2 text-xs font-mono font-bold uppercase tracking-wider text-[#F0F4F8]">
              <Terminal className="w-4 h-4 text-[#00E5C0]" />
              <span>Verify with Android SDK apksigner</span>
            </div>

            <div className="relative flex items-center justify-between gap-4 p-4 rounded-xl bg-[#12161C] border border-[#2A3140]">
              <code className="text-xs sm:text-sm font-mono text-[#00E5C0] break-all select-all">
                apksigner verify --print-certs crunch-v1.0.7-universal-release.apk
              </code>
              <button
                onClick={copyCommand}
                className="shrink-0 p-2 rounded-lg bg-[#1A1F27] hover:bg-[#222933] text-[#8A9BB0] hover:text-[#00E5C0] border border-[#2A3140] transition-colors"
                title="Copy verification command"
              >
                {copiedCommand ? (
                  <Check className="w-4 h-4 text-[#00E5C0]" />
                ) : (
                  <Copy className="w-4 h-4" />
                )}
              </button>
            </div>

            <div className="flex items-center justify-between text-xs text-[#8A9BB0]">
              <span>Compare the printed Signer #1 SHA-256 digest with the digest above.</span>
              <a
                href={APP_CONFIG.securityUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-[#00E5C0] hover:underline inline-flex items-center gap-1 font-medium"
              >
                Read full SECURITY.md <ExternalLink className="w-3 h-3" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

