'use client';

import { useEffect, useState } from 'react';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';

const STATUSES = ['all', 'pending', 'in_progress', 'succeeded', 'failed', 'cancelled', 'rolled_back'];

export default function DeploymentsPage() {
  const [deployments, setDeployments] = useState<any[]>([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    const params = statusFilter !== 'all' ? `?status=${statusFilter}` : '';
    apiFetch(`/api/v1/deployments${params}`)
      .then((res) => setDeployments(res.data || []))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [statusFilter]);

  return (
    <AppShell>
      <div className="page-header">
        <div className="flex items-center justify-between">
          <div>
            <h2>Deployments</h2>
            <p>Track all deployment activity across environments</p>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-2 mb-4" style={{ flexWrap: 'wrap' }}>
        {STATUSES.map((s) => (
          <button
            key={s}
            className={`btn ${statusFilter === s ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => setStatusFilter(s)}
            style={{ padding: '6px 14px', fontSize: '0.8rem' }}
          >
            {s.replace('_', ' ')}
          </button>
        ))}
      </div>

      {loading && <div className="loading-container"><div className="spinner" /><p>Loading…</p></div>}
      {error && <div className="error-container"><p className="error-msg">{error}</p></div>}

      {!loading && !error && (
        deployments.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 60 }}>
            <p style={{ fontSize: '2rem', marginBottom: 8 }}>🚀</p>
            <p className="text-muted">No deployments found</p>
          </div>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr><th>Status</th><th>Git Ref</th><th>SHA</th><th>Image</th><th>Duration</th><th>Error</th><th>Created</th></tr>
              </thead>
              <tbody>
                {deployments.map((d: any) => (
                  <tr key={d.id}>
                    <td><StatusBadge status={d.status} /></td>
                    <td>{d.gitRef || '—'}</td>
                    <td className="text-xs" style={{ fontFamily: 'monospace' }}>{d.gitSha ? d.gitSha.slice(0, 8) : '—'}</td>
                    <td>{d.imageTag || '—'}</td>
                    <td className="text-sm">{d.durationMs ? `${(d.durationMs / 1000).toFixed(1)}s` : '—'}</td>
                    <td className="text-sm" style={{ color: 'var(--error)', maxWidth: 200 }}>
                      <span className="truncate">{d.errorMessage || ''}</span>
                    </td>
                    <td className="text-sm text-muted">{formatDate(d.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
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
