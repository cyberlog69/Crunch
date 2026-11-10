import { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const baseUrl = "https://crunch.kappi.com";
  const releaseDate = new Date("2026-11-06T13:40:00Z");

  return [
    {
      url: baseUrl,
      lastModified: releaseDate,
      changeFrequency: "weekly",
      priority: 1.0,
    },
    {
      url: `${baseUrl}/download`,
      lastModified: releaseDate,
      changeFrequency: "weekly",
      priority: 0.9,
    },
    {
      url: `${baseUrl}/faq`,
      lastModified: releaseDate,
      changeFrequency: "monthly",
      priority: 0.7,
    },
    {
      url: `${baseUrl}/changelog`,
      lastModified: releaseDate,
      changeFrequency: "weekly",
      priority: 0.8,
    },
  ];
}
