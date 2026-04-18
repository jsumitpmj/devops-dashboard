'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

const navItems = [
  { href: '/dashboard', label: 'Overview', icon: '📊' },
  { href: '/projects', label: 'Projects', icon: '📁' },
  { href: '/deployments', label: 'Deployments', icon: '🚀' },
  { href: '/alerts', label: 'Alerts', icon: '🔔' },
  { href: '/audit-logs', label: 'Audit Logs', icon: '📋' },
];

export default function Sidebar() {
  const path = usePathname();
  const { user, logout } = useAuth();

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <h1>DevOps Dashboard</h1>
        <span>Platform Control Center</span>
      </div>
      <nav className="sidebar-nav">
        {navItems.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={path.startsWith(item.href) ? 'active' : ''}
          >
            <span>{item.icon}</span>
            {item.label}
          </Link>
        ))}
      </nav>
      <div className="sidebar-footer">
        {user && (
          <>
            <div style={{ marginBottom: 8 }}>
              <strong>{user.displayName || user.username}</strong>
              <br />
              <span className="text-xs text-muted">{user.roles.join(', ')}</span>
            </div>
            <button onClick={logout}>Sign out →</button>
          </>
        )}
      </div>
    </aside>
  );
}
