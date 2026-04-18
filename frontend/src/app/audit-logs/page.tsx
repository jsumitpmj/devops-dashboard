'use client';

import { useEffect, useState } from 'react';
import AppShell from '@/components/AppShell';
import { apiFetch } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { user } = useAuth();

  const isAdmin = user?.roles?.includes('admin');

  useEffect(() => {
    if (!isAdmin) {
      setLoading(false);
      return;
    }
    apiFetch('/api/v1/audit-logs')
      .then((res) => setLogs(res.data || []))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  return (
    <AppShell>
      <div className="page-header">
        <h2>Audit Logs</h2>
        <p>Track important actions across the platform</p>
      </div>

      {!isAdmin && (
        <div className="card" style={{ textAlign: 'center', padding: 60 }}>
          <p style={{ fontSize: '2rem', marginBottom: 8 }}>🔒</p>
          <p className="text-muted">Audit logs are restricted to administrators</p>
        </div>
      )}

      {isAdmin && loading && <div className="loading-container"><div className="spinner" /><p>Loading…</p></div>}
      {isAdmin && error && <div className="error-container"><p className="error-msg">{error}</p></div>}

      {isAdmin && !loading && !error && (
        logs.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 60 }}>
            <p style={{ fontSize: '2rem', marginBottom: 8 }}>📋</p>
            <p className="text-muted">No audit logs recorded yet</p>
          </div>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr><th>Action</th><th>Entity</th><th>Entity ID</th><th>Actor</th><th>Time</th></tr>
              </thead>
              <tbody>
                {logs.map((log: any) => (
                  <tr key={log.id}>
                    <td>
                      <span className={`badge ${actionBadge(log.action)}`}>{log.action}</span>
                    </td>
                    <td className="text-sm">{log.entityType || '—'}</td>
                    <td className="text-xs" style={{ fontFamily: 'monospace' }}>
                      {log.entityId ? log.entityId.slice(0, 8) + '…' : '—'}
                    </td>
                    <td className="text-xs" style={{ fontFamily: 'monospace' }}>
                      {log.actorId ? log.actorId.slice(0, 8) + '…' : 'system'}
                    </td>
                    <td className="text-sm text-muted">{formatDate(log.createdAt)}</td>
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

function actionBadge(action: string) {
  if (action.includes('created') || action.includes('login')) return 'badge-success';
  if (action.includes('failed') || action.includes('timed_out')) return 'badge-error';
  if (action.includes('escalated')) return 'badge-warning';
  return 'badge-info';
}

function formatDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
