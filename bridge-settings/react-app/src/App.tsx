import { useSettings } from "./hooks/useSettings";
import { ConnectionStatus } from "./components/ConnectionStatus";
import { SystemStatus } from "./components/SystemStatus";
import { SystemSettings } from "./components/SystemSettings";
import { AppSettings } from "./components/AppSettings";
import { MABLStatus } from "./components/MABLStatus";
import { ESimSettings } from "./components/ESimSettings";
import { LogViewer } from "./components/LogViewer";

export const App = () => {
  const {
    loading,
    error,
    connected,
    executeAction,
    actionResults,
    executionStatus,
  } = useSettings();

  if (loading) {
    return (
      <div className="app">
        <div className="loading">
          <div>Loading settings...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="header">
        <h1>PenumbraOS Settings</h1>
        <ConnectionStatus />
      </header>

      {error && <div className="error">Error: {error}</div>}

      {connected && (
        <>
          <SystemStatus />
          <LogViewer />

          <div className="settings-grid">
            <SystemSettings />
            <AppSettings />
            <MABLStatus />
            <ESimSettings
              onExecuteAction={executeAction}
              actionResults={actionResults}
              executionStatus={executionStatus}
            />
          </div>
        </>
      )}

      {!connected && !loading && (
        <div className="settings-section">
          <h2>Connection Required</h2>
          <p>
            Please ensure the settings service is running and try refreshing the
            page.
          </p>
        </div>
      )}
    </div>
  );
};
