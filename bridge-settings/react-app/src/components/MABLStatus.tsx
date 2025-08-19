import React from "react";
import { useMABLStatus } from "../hooks/useMABLStatus";

export const MABLStatus: React.FC = () => {
  const { conversationStatus, recentEvents, isActive, clearEvents } =
    useMABLStatus();

  if (!isActive) {
    return (
      <div className="settings-section">
        <h2>MABL Assistant</h2>
        <p style={{ color: "#7f8c8d", fontStyle: "italic" }}>
          MABL is not currently active. Start a conversation to see status
          updates.
        </p>
      </div>
    );
  }

  const getStateColor = (state: string) => {
    switch (state) {
      case "transcribing":
        return "#f39c12";
      case "aiThinking":
        return "#9b59b6";
      case "aiResponding":
        return "#3498db";
      case "error":
        return "#e74c3c";
      default:
        return "#27ae60";
    }
  };

  const getStateLabel = (state: string) => {
    switch (state) {
      case "transcribing":
        return "Listening...";
      case "aiThinking":
        return "AI Thinking...";
      case "aiResponding":
        return "AI Responding...";
      case "error":
        return "Error";
      default:
        return "Ready";
    }
  };

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString();
  };

  const getEventIcon = (type: string) => {
    switch (type) {
      case "userMessage":
        return "👤";
      case "aiResponse":
        return "🤖";
      case "touchpadTap":
        return "👆";
      case "sttError":
        return "🔊❌";
      case "llmError":
        return "🤖❌";
      default:
        return "📝";
    }
  };

  const getEventDescription = (event: any) => {
    switch (event.type) {
      case "userMessage":
        return `User: ${event.data.text || "Message sent"}`;
      case "aiResponse":
        return `AI: ${event.data.text?.substring(0, 50) || "Response"}${
          event.data.text?.length > 50 ? "..." : ""
        }`;
      case "touchpadTap":
        return `Touchpad ${event.data.tapType} tap (${event.data.duration}ms)`;
      case "sttError":
        return `Speech recognition error: ${event.data.error}`;
      case "llmError":
        return `AI error: ${event.data.error}`;
      default:
        return `${event.type}: ${JSON.stringify(event.data)}`;
    }
  };

  return (
    <div className="settings-section">
      <h2>MABL Assistant</h2>

      {/* Current Status */}
      <div className="mabl-status">
        <h3>Current Status</h3>
        <div className="status-grid">
          <div className="status-item">
            <span className="label">State:</span>
            <span
              className="value"
              style={{
                color: getStateColor(conversationStatus.state),
                fontWeight: "bold",
              }}
            >
              {getStateLabel(conversationStatus.state)}
            </span>
          </div>

          {conversationStatus.partialText && (
            <div className="status-item">
              <span className="label">Transcribing:</span>
              <span className="value partial-text">
                "{conversationStatus.partialText}"
              </span>
            </div>
          )}

          {conversationStatus.userMessage && (
            <div className="status-item">
              <span className="label">User Message:</span>
              <span className="value">
                "{conversationStatus.userMessage.substring(0, 50)}
                {conversationStatus.userMessage.length > 50 ? "..." : ""}"
              </span>
            </div>
          )}

          {conversationStatus.streamingToken && (
            <div className="status-item">
              <span className="label">AI Token:</span>
              <span className="value streaming-token">
                "{conversationStatus.streamingToken}"
              </span>
            </div>
          )}

          {conversationStatus.lastResponse && (
            <div className="status-item">
              <span className="label">Last Response:</span>
              <span className="value">
                "{conversationStatus.lastResponse.substring(0, 50)}
                {conversationStatus.lastResponse.length > 50 ? "..." : ""}"
              </span>
            </div>
          )}

          {conversationStatus.errorMessage && (
            <div className="status-item">
              <span className="label">Error:</span>
              <span className="value" style={{ color: "#e74c3c" }}>
                {conversationStatus.errorMessage}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Recent Events */}
      <div className="mabl-events">
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "12px",
          }}
        >
          <h3>Recent Events</h3>
          <button
            onClick={clearEvents}
            style={{
              background: "#e74c3c",
              color: "white",
              border: "none",
              padding: "4px 8px",
              borderRadius: "4px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            Clear
          </button>
        </div>

        {recentEvents.length === 0 ? (
          <p style={{ color: "#7f8c8d", fontStyle: "italic" }}>
            No recent events
          </p>
        ) : (
          <div className="events-list">
            {recentEvents.slice(0, 10).map((event) => (
              <div key={event.id} className="event-item">
                <div className="event-header">
                  <span className="event-icon">{getEventIcon(event.type)}</span>
                  <span className="event-time">
                    {formatTimestamp(event.timestamp)}
                  </span>
                </div>
                <div className="event-description">
                  {getEventDescription(event)}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
