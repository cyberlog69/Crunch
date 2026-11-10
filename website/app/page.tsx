import { HeroSection } from "@/components/HeroSection";
import { FeaturesGrid } from "@/components/FeaturesGrid";
import { HowItWorks } from "@/components/HowItWorks";
import { DownloadCards } from "@/components/DownloadCards";
import { PlayProtectNote } from "@/components/PlayProtectNote";
import { SecurityBadge } from "@/components/SecurityBadge";

export default function HomePage() {
  return (
    <div className="flex flex-col">
      <HeroSection />
      <FeaturesGrid />
      <HowItWorks />
      <DownloadCards />
      <PlayProtectNote />
      <SecurityBadge />
    </div>
  );
}
