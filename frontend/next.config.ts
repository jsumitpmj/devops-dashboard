import type { NextConfig } from 'next';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000';
const AUTH_URL = process.env.NEXT_PUBLIC_AUTH_URL || 'http://localhost:4001';

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: '/api/v1/:path*', destination: `${API_URL}/api/v1/:path*` },
      { source: '/auth/:path*',   destination: `${AUTH_URL}/auth/:path*` },
    ];
  },
};

export default nextConfig;
