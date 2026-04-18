# Frontend — Next.js Dashboard

A modern dark-themed dashboard for the DevOps Platform.

## Tech Stack

- **Next.js 15** with App Router
- **React 19**
- **Recharts** for data visualization
- **Vanilla CSS** with custom properties design system

## Directory Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── globals.css          # Design system (tokens, components, utilities)
│   │   ├── layout.tsx           # Root layout with AuthProvider
│   │   ├── page.tsx             # Root redirect (→ /dashboard or /login)
│   │   ├── login/page.tsx       # Login + register form
│   │   ├── dashboard/page.tsx   # Overview: stats cards + recent deployments
│   │   ├── projects/page.tsx    # Project card grid
│   │   ├── projects/[id]/       # Project detail: envs + deploy history
│   │   ├── deployments/page.tsx # Filterable deployment table
│   │   ├── alerts/page.tsx      # Alert list with severity/status badges
│   │   └── audit-logs/page.tsx  # Admin-only audit trail
│   ├── components/
│   │   ├── AppShell.tsx         # Auth guard + sidebar + main layout
│   │   └── Sidebar.tsx          # Navigation with active states
│   └── lib/
│       ├── api.ts               # API client + token management
│       └── auth-context.tsx     # AuthProvider + useAuth() hook
├── next.config.ts               # API proxy rewrites
├── .env.local                   # Local API URLs
└── Dockerfile                   # Multi-stage Node 22 build
```

## Running Locally

```bash
# With backend running via docker compose
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

The dev server proxies API calls automatically:
- `/api/v1/*` → `http://localhost:4000` (API service)
- `/auth/*` → `http://localhost:4001` (Auth service)

## Design System

The CSS uses a token-based approach with `var(--token)` custom properties:

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-primary` | `#0a0e1a` | Page background |
| `--bg-card` | `#1a2236` | Card backgrounds |
| `--accent` | `#3b82f6` | Primary actions, links |
| `--success` | `#10b981` | Success states |
| `--warning` | `#f59e0b` | Warning states |
| `--error` | `#ef4444` | Error states |
