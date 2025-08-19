import { useState, useEffect, useRef } from "react";
import { websocketService } from "../services/websocketService";

interface LogEntry {
  timestamp: number;
  level: string;
  tag: string;
  message: string;
}

export const LogViewer = () => {
  const [isStreaming, setIsStreaming] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [activeTab, setActiveTab] = useState("all");
  const [isExpanded, setIsExpanded] = useState(false);
  const logContainerRef = useRef<HTMLDivElement>(null);
  const [autoScroll, setAutoScroll] = useState(true);

  const logTabs = [
    { id: "all", label: "All Logs", filter: () => true },
    {
      id: "error",
      label: "Errors",
      filter: (log: LogEntry) => log.level === "E",
    },
    {
      id: "warning",
      label: "Warnings +",
      filter: (log: LogEntry) => ["W", "E"].includes(log.level),
    },
    {
      id: "info",
      label: "Info +",
      filter: (log: LogEntry) => ["I", "D", "W", "E"].includes(log.level),
    },
    {
      id: "penumbra",
      label: "PenumbraOS",
      filter: (log: LogEntry) =>
        /pinitd|penumbra|SystemBridgeService|BridgeCoreService/.test(log.tag),
    },
  ];

  useEffect(() => {
    if (!isStreaming) {
      return;
    }

    return websocketService.onMessage((message) => {
      if (message.type === "logEntry") {
        const logEntry: LogEntry = {
          timestamp: message.timestamp,
          level: message.level,
          tag: message.tag,
          message: message.message,
        };

        setLogs((prev) => {
          const newLogs = [...prev, logEntry];
          // Keep only last 1000 logs to prevent memory issues
          return newLogs.slice(-1000);
        });
      }
    });
  }, [isStreaming]);

  useEffect(() => {
    if (autoScroll && logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [logs, autoScroll]);

  const startStreaming = () => {
    setIsStreaming(true);
    websocketService.send({
      type: "executeAction",
      appId: "system",
      action: "startLogStream",
      params: {},
    });
  };

  const stopStreaming = () => {
    setIsStreaming(false);
    websocketService.send({
      type: "executeAction",
      appId: "system",
      action: "stopLogStream",
      params: {},
    });
  };

  const clearLogs = () => {
    setLogs([]);
  };

  const exportLogs = () => {
    // Download logs directly via HTTP endpoint
    const link = document.createElement("a");
    link.href = "/api/logs/download";
    link.download = `PenumbraOS_Logs_${Date.now()}.zip`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const filteredLogs = logs.filter(
    logTabs.find((tab) => tab.id === activeTab)?.filter || (() => true)
  );

  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp);
    const timeString = date.toLocaleTimeString("en-US", {
      hour12: false,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    const milliseconds = date.getMilliseconds().toString().padStart(3, "0");
    return `${timeString}.${milliseconds}`;
  };

  const getLevelColor = (level: string) => {
    switch (level) {
      case "E":
        return "#ff4444";
      case "W":
        return "#ffaa00";
      case "I":
        return "#00aa00";
      case "D":
        return "#0088cc";
      default:
        return "#666666";
    }
  };

  return (
    <div className="settings-section log-viewer">
      <div
        className="log-viewer-header"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <h2>
          <span className="expand-icon">{isExpanded ? "▼" : "▶"}</span>
          System Logs
        </h2>
        <div className="log-status">
          {isStreaming && (
            <span className="streaming-indicator">🔴 Streaming</span>
          )}
        </div>
      </div>

      {isExpanded && (
        <>
          <div className="log-controls">
            <button
              className={`action-button ${isStreaming ? "danger" : "primary"}`}
              onClick={isStreaming ? stopStreaming : startStreaming}
            >
              {isStreaming ? "Stop Streaming" : "Start Streaming"}
            </button>
            <button className="action-button" onClick={clearLogs}>
              Clear
            </button>
            <label className="auto-scroll-toggle">
              <input
                type="checkbox"
                checked={autoScroll}
                onChange={(e) => setAutoScroll(e.target.checked)}
              />
              Auto-scroll
            </label>
            {/* Spacer */}
            <span />
            <button className="action-button" onClick={exportLogs}>
              Export All Logs
            </button>
          </div>

          <div className="log-tabs">
            {logTabs.map((tab) => (
              <button
                key={tab.id}
                className={`log-tab ${activeTab === tab.id ? "active" : ""}`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="log-container" ref={logContainerRef}>
            {filteredLogs.length === 0 ? (
              <div className="log-empty">
                {isStreaming
                  ? "Waiting for logs..."
                  : "No logs to display. Start streaming to see logs."}
              </div>
            ) : (
              filteredLogs.map((log, index) => (
                <div
                  key={index}
                  className="log-entry"
                  style={{ color: getLevelColor(log.level) }}
                >
                  <span className="log-timestamp">
                    {formatTimestamp(log.timestamp)}
                  </span>
                  <span className="log-level">{log.level}</span>
                  <span className="log-tag">{log.tag}</span>
                  <span className="log-message">{log.message}</span>
                </div>
              ))
            )}
          </div>

          <div className="log-footer">
            <span className="log-count">
              Showing {filteredLogs.length} of {logs.length} logs
            </span>
            {isStreaming && (
              <span className="streaming-indicator">🔴 Streaming</span>
            )}
          </div>
        </>
      )}
    </div>
  );
};
