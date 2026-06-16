import { DEMO_USER_EMAIL } from '../api';
import { TabKey } from '../types';

type AppHeaderProps = {
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
};

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'purchase', label: 'Purchase Demo' },
  { key: 'dashboard', label: 'System Dashboard' },
  { key: 'how', label: 'How It Works' }
];

export function AppHeader({ activeTab, onTabChange }: AppHeaderProps) {
  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">TicketForge Demo</p>
        <h1>TicketForge</h1>
        <p className="subtitle">
          High-concurrency ticketing-system lab for atomic inventory reservation, idempotent ordering,
          payment callbacks, expiration handling, concurrency correctness and observability.
        </p>
      </div>
      <div className="user-badge">
        <span>Demo User</span>
        <strong>{DEMO_USER_EMAIL}</strong>
      </div>
      <nav className="tabs" aria-label="Primary">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            className={activeTab === tab.key ? 'tab active' : 'tab'}
            type="button"
            onClick={() => onTabChange(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
    </header>
  );
}
