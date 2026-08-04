import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "img.kleinanzeigen.de",
      },
      {
        protocol: "https",
        hostname: "static.kleinanzeigen.de",
      },
      {
        protocol: "https",
        hostname: "pictures.immobilienscout24.de",
      },
    ],
  },
};

export default config;
