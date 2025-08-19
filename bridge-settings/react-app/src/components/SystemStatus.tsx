import React from "react";
import { useSettings } from "../hooks/useSettings";

export const SystemStatus: React.FC = () => {
  const { systemStatus } = useSettings();

  return (
    <div className="system-status">
      <h3>System Status</h3>

      <div className="status-grid">
        {systemStatus.battery && (
          <div>
            <div className="status-item">
              <span className="label">Battery Level:</span>
              <span className="value">{systemStatus.battery.level}%</span>
            </div>
            <div className="status-item">
              <span className="label">Charging:</span>
              <span className="value">
                {systemStatus.battery.charging ? "Yes" : "No"}
              </span>
            </div>
            <div className="status-item">
              <span className="label">Power Save:</span>
              <span className="value">
                {systemStatus.battery.powerSaveMode ? "On" : "Off"}
              </span>
            </div>
          </div>
        )}

        {systemStatus.display && (
          <div>
            <div className="status-item">
              <span className="label">Brightness:</span>
              <span className="value">{systemStatus.display.brightness}%</span>
            </div>
            <div className="status-item">
              <span className="label">Auto Brightness:</span>
              <span className="value">
                {systemStatus.display.autoBrightness ? "On" : "Off"}
              </span>
            </div>
          </div>
        )}

        {systemStatus.audio && (
          <div>
            <div className="status-item">
              <span className="label">Volume:</span>
              <span className="value">{systemStatus.audio.volume}%</span>
            </div>
            <div className="status-item">
              <span className="label">Muted:</span>
              <span className="value">
                {systemStatus.audio.muted ? "Yes" : "No"}
              </span>
            </div>
          </div>
        )}

        {systemStatus.network && (
          <div>
            <div className="status-item">
              <span className="label">WiFi:</span>
              <span className="value">
                {systemStatus.network.wifiEnabled ? "Enabled" : "Disabled"}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
