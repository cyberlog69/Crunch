import { APP_CONFIG } from "./constants";

export interface GitHubAsset {
  id: number;
  name: string;
  size: number;
  download_count: number;
  browser_download_url: string;
  content_type: string;
}

export interface GitHubRelease {
  id: number;
  tag_name: string;
  name: string;
  body: string;
  published_at: string;
  html_url: string;
  prerelease: boolean;
  assets: GitHubAsset[];
}

const FALLBACK_RELEASES: GitHubRelease[] = [
  {
    id: 1,
    tag_name: "v1.0.7",
    name: "Crunch v1.0.7 — Storage Persistence & Keystore Hardening",
    published_at: "2026-11-06T13:40:00Z",
    html_url: "https://github.com/cyberlog69/Crunch/releases/tag/v1.0.7",
    prerelease: false,
    body: `### What's New in v1.0.7
- **Permanent Keystore Signing**: Released with official RSA-2048 keystore to guarantee seamless in-place updates.
- **Download History Persistence**: Integrated \`DownloadHistorySync\` scanner to restore history from public storage (\`Downloads/Crunch\`) across updates and reinstalls.
- **Errno 30 / Read-Only Filesystem Fix**: Custom yt-dlp cache & temp storage isolated to Android internal files directory.
- **Long Filename Fix**: Truncates sanitized filenames to prevent filesystem Errno 36 issues.
- **Hardened Security**: Removed \`REQUEST_INSTALL_PACKAGES\` to align strictly with least-privilege principles.
- **Settings UI Polish**: Clean toggle badges, connection presets, and dedicated component status indicators.`,
    assets: [
      {
        id: 101,
        name: "crunch-v1.0.7-universal-release.apk",
        size: 120586240,
        download_count: 840,
        browser_download_url: "https://github.com/cyberlog69/Crunch/releases/download/v1.0.7/crunch-v1.0.7-universal-release.apk",
        content_type: "application/vnd.android.package-archive",
      },
      {
        id: 102,
        name: "crunch-v1.0.7-arm64-v8a-release.apk",
        size: 60817408,
        download_count: 2450,
        browser_download_url: "https://github.com/cyberlog69/Crunch/releases/download/v1.0.7/crunch-v1.0.7-arm64-v8a-release.apk",
        content_type: "application/vnd.android.package-archive",
      },
      {
        id: 103,
        name: "crunch-v1.0.7-armeabi-v7a-release.apk",
        size: 56623104,
        download_count: 420,
        browser_download_url: "https://github.com/cyberlog69/Crunch/releases/download/v1.0.7/crunch-v1.0.7-armeabi-v7a-release.apk",
        content_type: "application/vnd.android.package-archive",
      },
      {
        id: 104,
        name: "crunch-v1.0.7-x86_64-release.apk",
        size: 65011712,
        download_count: 180,
        browser_download_url: "https://github.com/cyberlog69/Crunch/releases/download/v1.0.7/crunch-v1.0.7-x86_64-release.apk",
        content_type: "application/vnd.android.package-archive",
      },
    ],
  },
  {
    id: 2,
    tag_name: "v1.0.6",
    name: "Crunch v1.0.6 — Engine Sync & Background Services",
    published_at: "2026-10-28T18:20:00Z",
    html_url: "https://github.com/cyberlog69/Crunch/releases/tag/v1.0.6",
    prerelease: false,
    body: `### Changes in v1.0.6
- Native NDK FFmpeg 7.1.1 upgrade with hardware HEVC/AVC muxing.
- aria2c 1.37.0 parallel chunk streaming engine.
- Dynamic Material You UI with automatic dark mode.
- In-app component version tracking.`,
    assets: [],
  },
];

export async function getLatestReleases(): Promise<GitHubRelease[]> {
  try {
    const headers: Record<string, string> = {
      Accept: "application/vnd.github.v3+json",
      "User-Agent": "Crunch-Website",
    };

    if (process.env.GITHUB_TOKEN) {
      headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    }

    const res = await fetch("https://api.github.com/repos/cyberlog69/Crunch/releases?per_page=10", {
      headers,
      next: { revalidate: 60 }, // ISR every 60 seconds
    });

    if (!res.ok) {
      console.warn(`GitHub API returned status ${res.status}. Using fallback release data.`);
      return FALLBACK_RELEASES;
    }

    const data: GitHubRelease[] = await res.json();
    return data && data.length > 0 ? data : FALLBACK_RELEASES;
  } catch (error) {
    console.error("Failed to fetch releases from GitHub API:", error);
    return FALLBACK_RELEASES;
  }
}

export async function getLatestRelease(): Promise<GitHubRelease> {
  const releases = await getLatestReleases();
  return releases[0] || FALLBACK_RELEASES[0];
}

export function formatBytes(bytes: number, decimals = 1): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + " " + sizes[i];
}

