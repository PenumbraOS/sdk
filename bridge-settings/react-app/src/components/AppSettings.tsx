import React from "react";
import { useSettings } from "../hooks/useSettings";
import ToggleSwitch from "./ToggleSwitch";
import Slider from "./Slider";
import TextInput from "./TextInput";

export const AppSettings: React.FC = () => {
  const { allSettings } = useSettings();

  // Extract app settings (anything that's not 'system')
  const appSettingsEntries = Object.entries(allSettings).filter(
    ([key]) => key !== "system"
  );

  if (appSettingsEntries.length === 0) {
    return (
      <div className="settings-section">
        <h2>App Settings</h2>
        <p style={{ color: "#7f8c8d", fontStyle: "italic" }}>
          No app settings registered yet. Apps will appear here when they
          register settings through the SDK.
        </p>
      </div>
    );
  }

  return (
    <div className="settings-section">
      <h2>App Settings</h2>
      {appSettingsEntries.map(([appId, settings]) => (
        <div key={appId} style={{ marginBottom: "20px" }}>
          <h3
            style={{
              color: "#34495e",
              fontSize: "1rem",
              marginBottom: "12px",
              borderBottom: "1px solid #ecf0f1",
              paddingBottom: "4px",
            }}
          >
            {appId}
          </h3>
          {Object.entries(settings).map(([category, categoryContents]) => {
            return Object.entries(
              categoryContents as Record<string, unknown>
            ).map(([property, value]) => (
              <div key={`${category}.${property}`} className="setting-item">
                <span className="setting-label">{property}</span>
                <div className="setting-control">
                  <SettingControlView
                    appId={appId}
                    category={category}
                    property={property}
                    value={value}
                  />
                </div>
              </div>
            ));
          })}
        </div>
      ))}
    </div>
  );
};

const SettingControlView: React.FC<{
  appId: string;
  category: string;
  property: string;
  value: unknown;
}> = ({ appId, category, property, value }) => {
  const { updateAppSetting } = useSettings();

  if (typeof value === "boolean") {
    return (
      <ToggleSwitch
        enabled={value}
        onChange={(enabled) =>
          updateAppSetting(appId, category, property, enabled)
        }
      />
    );
  } else if (typeof value === "number") {
    return (
      <div className="setting-control">
        <Slider
          value={value}
          onChange={(newValue) =>
            updateAppSetting(appId, category, property, newValue)
          }
        />
        <span className="status-display">{value}</span>
      </div>
    );
  } else {
    return (
      <TextInput
        value={String(value)}
        onChange={(newValue) =>
          updateAppSetting(appId, category, property, newValue)
        }
      />
    );
  }
};
