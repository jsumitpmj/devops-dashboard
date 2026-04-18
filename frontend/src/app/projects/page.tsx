'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';

export default function ProjectsPage() {
  const [projects, setProjects] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    apiFetch('/api/v1/projects')
      .then((res) => setProjects(res.data || []))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <AppShell>
      <div className="page-header">
        <h2>Projects</h2>
        <p>Manage your platform projects and their environments</p>
      </div>

      {loading && <div className="loading-container"><div className="spinner" /><p>Loading projects…</p></div>}
      {error && <div className="error-container"><p className="error-msg">{error}</p></div>}

      {!loading && !error && (
        projects.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 60 }}>
            <p style={{ fontSize: '2rem', marginBottom: 8 }}>📁</p>
            <p className="text-muted">No projects yet. Create one to get started.</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 20 }}>
            {projects.map((p: any) => (
              <Link key={p.id} href={`/projects/${p.id}`} style={{ textDecoration: 'none' }}>
                <div className="card" style={{ cursor: 'pointer' }}>
                  <div className="flex items-center justify-between mb-4">
                    <h3 style={{ fontWeight: 700, fontSize: '1.05rem' }}>{p.name}</h3>
                    <span className="badge badge-success">active</span>
                  </div>
                  <p className="text-sm text-muted" style={{ marginBottom: 12 }}>
                    {p.description || 'No description'}
                  </p>
                  <div className="flex items-center gap-4 text-xs text-muted">
                    <span>🏷️ {p.slug}</span>
                    {p.repoUrl && <span>🔗 {new URL(p.repoUrl).pathname.slice(1)}</span>}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )
      )}
    </AppShell>
  );
}
