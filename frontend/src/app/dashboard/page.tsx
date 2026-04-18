'use client';

import { useEffect, useState } from 'react';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';

interface Stats {
  projects: number;
  deployments: { pending: number; total: number };
  alerts: number;
  recentDeployments: any[];
}

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [projRes, deployStats, alertRes, deployRes] = await Promise.all([
          apiFetch('/api/v1/projects'),
          apiFetch('/api/v1/deployments/stats'),
          apiFetch('/api/v1/alerts?size=5'),
          apiFetch('/api/v1/deployments?size=5'),
        ]);
        setStats({
          projects: projRes.total || 0,
          deployments: { pending: deployStats.pending || 0, total: deployStats.total || 0 },
          alerts: alertRes.total || 0,
          recentDeployments: deployRes.data || [],
        });
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <AppShell>
      <div className="page-header">
        <h2>Dashboard Overview</h2>
        <p>Platform health and recent activity at a glance</p>
      </div>

      {loading && (
        <div className="loading-container"><div className="spinner" /><p>Loading dashboard…</p></div>
      )}

      {error && (
        <div className="error-container"><p className="error-msg">{error}</p></div>
      )}

      {stats && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Projects</div>
              <div className="stat-value" style={{ color: 'var(--accent)' }}>{stats.projects}</div>
              <div className="stat-sub">Active projects</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Total Deployments</div>
              <div className="stat-value" style={{ color: 'var(--success)' }}>{stats.deployments.total}</div>
              <div className="stat-sub">All time</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Pending</div>
              <div className="stat-value" style={{ color: 'var(--warning)' }}>{stats.deployments.pending}</div>
              <div className="stat-sub">Awaiting execution</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Active Alerts</div>
              <div className="stat-value" style={{ color: 'var(--error)' }}>{stats.alerts}</div>
              <div className="stat-sub">Require attention</div>
            </div>
          </div>

          <div className="card">
            <h3 style={{ marginBottom: 16, fontWeight: 700 }}>Recent Deployments</h3>
            {stats.recentDeployments.length === 0 ? (
              <p className="text-muted text-sm">No deployments yet</p>
            ) : (
              <div className="table-container" style={{ border: 'none' }}>
                <table>
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Git Ref</th>
                      <th>Image Tag</th>
                      <th>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.recentDeployments.map((d: any) => (
                      <tr key={d.id}>
                        <td><StatusBadge status={d.status} /></td>
                        <td>{d.gitRef || '—'}</td>
                        <td>{d.imageTag || '—'}</td>
                        <td className="text-sm text-muted">{formatDate(d.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
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
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}
