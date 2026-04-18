'use client';

import { useEffect, useState } from 'react';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';

const SEVERITY_MAP: Record<string, string> = {
  critical: 'badge-error',
  error: 'badge-error',
  warning: 'badge-warning',
  info: 'badge-info',
};

const STATUS_MAP: Record<string, string> = {
  triggered: 'badge-error',
  acknowledged: 'badge-warning',
  resolved: 'badge-success',
};

export default function AlertsPage() {
  const [alerts, setAlerts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    apiFetch('/api/v1/alerts')
      .then((res) => setAlerts(res.data || []))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <AppShell>
      <div className="page-header">
        <h2>Alerts</h2>
        <p>System alerts and notification history</p>
      </div>

      {loading && <div className="loading-container"><div className="spinner" /><p>Loading alerts…</p></div>}
      {error && <div className="error-container"><p className="error-msg">{error}</p></div>}

      {!loading && !error && (
        alerts.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 60 }}>
            <p style={{ fontSize: '2rem', marginBottom: 8 }}>🔔</p>
            <p className="text-muted">No alerts — everything looks good!</p>
          </div>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr><th>Severity</th><th>Status</th><th>Title</th><th>Source</th><th>Created</th></tr>
              </thead>
              <tbody>
                {alerts.map((a: any) => (
                  <tr key={a.id}>
                    <td><span className={`badge ${SEVERITY_MAP[a.severity] || 'badge-neutral'}`}>{a.severity}</span></td>
                    <td><span className={`badge ${STATUS_MAP[a.status] || 'badge-neutral'}`}>{a.status}</span></td>
                    <td>
                      <strong style={{ color: 'var(--text-primary)' }}>{a.title}</strong>
                      {a.message && <p className="text-xs text-muted" style={{ marginTop: 4 }}>{a.message}</p>}
                    </td>
                    <td className="text-sm text-muted">{a.source || '—'}</td>
                    <td className="text-sm text-muted">{formatDate(a.createdAt)}</td>
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

function formatDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
