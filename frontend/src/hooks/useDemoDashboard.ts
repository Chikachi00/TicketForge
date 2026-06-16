import { useCallback, useEffect, useState } from 'react';
import { fetchDemoDashboard, fetchDemoProfile, fetchHealth, resetDemoData } from '../api';
import { DemoDashboard, DemoProfile, LoadState } from '../types';

export function useDemoDashboard() {
  const [health, setHealth] = useState<string>('unknown');
  const [profile, setProfile] = useState<DemoProfile | null>(null);
  const [dashboard, setDashboard] = useState<DemoDashboard | null>(null);
  const [state, setState] = useState<LoadState>('idle');
  const [error, setError] = useState<string | null>(null);
  const [resetting, setResetting] = useState(false);
  const [resetMessage, setResetMessage] = useState<string | null>(null);

  const refreshDashboard = useCallback(() => {
    setState('loading');
    setError(null);
    return Promise.all([
      fetchHealth().catch(() => ({ status: 'DOWN' })),
      fetchDemoProfile(),
      fetchDemoDashboard()
    ])
      .then(([healthResponse, profileResponse, dashboardResponse]) => {
        setHealth(healthResponse.status);
        setProfile(profileResponse);
        setDashboard(dashboardResponse);
        setState('loaded');
        return dashboardResponse;
      })
      .catch((exception: Error) => {
        setHealth('unknown');
        setError(exception.message || 'Start the backend with the demo profile to enable reset and dashboard tools.');
        setState('error');
        throw exception;
      });
  }, []);

  const resetDashboard = useCallback(() => {
    setResetting(true);
    setResetMessage(null);
    return resetDemoData()
      .then((response) => {
        setResetMessage(`Demo data reset. Deleted ${response.deletedOrders} orders and ${response.deletedPayments} payment records.`);
        return refreshDashboard();
      })
      .finally(() => setResetting(false));
  }, [refreshDashboard]);

  useEffect(() => {
    refreshDashboard().catch(() => undefined);
  }, [refreshDashboard]);

  return {
    health,
    profile,
    dashboard,
    state,
    error,
    resetting,
    resetMessage,
    refreshDashboard,
    resetDashboard,
    setResetMessage
  };
}
