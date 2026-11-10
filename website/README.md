# Crunch Official Website

The official marketing and download hub for **Crunch — High-Speed Media & Stream Engine for Android**.

---

## 🚀 Features

- **Next.js 16 (App Router)** with React 19, TypeScript, and Tailwind CSS.
- **Geist Variable Typography** matching the app's modern Material You aesthetic.
- **Theme Palette**: Deep AMOLED `#12161C` background with `#00E5C0` teal accent.
- **Dynamic GitHub Releases (ISR)**: Auto-fetches latest APK release assets and notes from GitHub API every 60 seconds with offline fallbacks.
- **Cryptographic Verification Hub**: Displays official RSA-2048 signing certificate SHA-256 fingerprints and one-click copyable `apksigner` verification commands.
- **Sideloading & Play Protect Guidance**: Interactive step-by-step instructions addressing Play Protect prompts.
- **Architecture Matrix**: Download cards for Universal, `arm64-v8a`, `armeabi-v7a`, and `x86_64` builds.
- **SEO & Social Previews**: Complete OpenGraph, Twitter Cards, `robots.ts`, and `sitemap.ts`.

---

## 🛠️ Local Development

```bash
cd website
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## 📦 Building for Production

```bash
npm run build
npm start
```

---

## 🌐 Deploying to Vercel

### Option 1: Monorepo Deployment
1. Import the repository on [Vercel](https://vercel.com/new).
2. Set **Root Directory** to `website`.
3. Framework Preset: **Next.js**.
4. (Optional) Set `GITHUB_TOKEN` environment variable to prevent GitHub API rate limits.
5. Click **Deploy**.

### Option 2: Dedicated Repository
You can copy or push `website/` into its own standalone repository (e.g. `cyberlog69/crunch-website`) and link it directly to Vercel.

---

## 📄 License

GPLv3 © [Crunch](https://github.com/cyberlog69/Crunch)
