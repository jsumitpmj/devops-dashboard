'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';

export default function ProjectDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const [project, setProject] = useState<any>(null);
  const [envs, setEnvs] = useState<any[]>([]);
  const [deployments, setDeployments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [projRes, envRes, depRes] = await Promise.all([
          apiFetch(`/api/v1/projects/${id}`),
          apiFetch(`/api/v1/environments?projectId=${id}`),
          apiFetch(`/api/v1/deployments?projectId=${id}&size=10`),
        ]);
        setProject(projRes.data);
        setEnvs(envRes.data || []);
        setDeployments(depRes.data || []);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  if (loading) return <AppShell><div className="loading-container"><div className="spinner" /><p>Loading project…</p></div></AppShell>;
  if (error) return <AppShell><div className="error-container"><p className="error-msg">{error}</p></div></AppShell>;
  if (!project) return <AppShell><div className="error-container"><p className="error-msg">Project not found</p></div></AppShell>;

  return (
    <AppShell>
      <div className="page-header">
        <h2>{project.name}</h2>
        <p>{project.description || project.slug}</p>
      </div>

      {/* Environments */}
      <h3 style={{ fontWeight: 700, marginBottom: 16 }}>🌐 Environments</h3>
      {envs.length === 0 ? (
        <div className="card mb-4" style={{ textAlign: 'center', padding: 40 }}>
          <p className="text-muted">No environments configured</p>
        </div>
      ) : (
        <div className="stats-grid" style={{ marginBottom: 32 }}>
          {envs.map((env: any) => (
            <div key={env.id} className="stat-card">
              <div className="flex items-center justify-between">
                <span className="stat-label" style={{ margin: 0 }}>{env.name}</span>
                <span className={`badge ${env.isLocked ? 'badge-error' : 'badge-success'}`}>
                  {env.isLocked ? '🔒 Locked' : '✅ Open'}
                </span>
              </div>
              <div className="stat-value" style={{ fontSize: '1.2rem', marginTop: 8 }}>{env.envType}</div>
              {env.url && <div className="stat-sub truncate">{env.url}</div>}
            </div>
          ))}
        </div>
      )}

      {/* Deployments */}
      <h3 style={{ fontWeight: 700, marginBottom: 16 }}>🚀 Recent Deployments</h3>
      {deployments.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <p className="text-muted">No deployments for this project</p>
        </div>
      ) : (
        <div className="table-container">
          <table>
            <thead>
              <tr><th>Status</th><th>Git Ref</th><th>Image</th><th>Duration</th><th>Created</th></tr>
            </thead>
            <tbody>
              {deployments.map((d: any) => (
                <tr key={d.id}>
                  <td><StatusBadge status={d.status} /></td>
                  <td>{d.gitRef || '—'}</td>
                  <td>{d.imageTag || '—'}</td>
                  <td className="text-sm">{d.durationMs ? `${(d.durationMs / 1000).toFixed(1)}s` : '—'}</td>
                  <td className="text-sm text-muted">{formatDate(d.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </AppShell>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    succeeded: 'badge-success', failed: 'badge-error', pending: 'badge-warning',
    in_progress: 'badge-info', cancelled: 'badge-neutral', rolled_back: 'badge-neutral',
  };
  return <span className={`badge ${map[status] || 'badge-neutral'}`}>{status}</span>;
}

function formatDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
