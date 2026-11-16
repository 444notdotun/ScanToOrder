import type { NextConfig } from "next";

// Where /api/v1 goes. Set BACKEND_URL to run against a backend of your own (for example http://localhost:8080).
const backend = (process.env.BACKEND_URL || "https://scantoorder.onrender.com").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${backend}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
