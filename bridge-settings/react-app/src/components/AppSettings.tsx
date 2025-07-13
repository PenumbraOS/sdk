import React from 'react';
import { useSettings } from '../hooks/useSettings';
import ToggleSwitch from './ToggleSwitch';
import Slider from './Slider';

const AppSettings: React.FC = () => {
  const { allSettings, updateAppSetting } = useSettings();

  // Extract app settings (anything that's not 'system')
  const appSettingsEntries = Object.entries(allSettings).filter(
    ([key]) => key !== 'system'
  );

  if (appSettingsEntries.length === 0) {
    return (
      <div className="settings-section">
        <h2>App Settings</h2>
        <p style={{ color: '#7f8c8d', fontStyle: 'italic' }}>
          No app settings registered yet. Apps will appear here when they register settings through the SDK.
        </p>
      </div>
    );
  }

  const handleAppSettingChange = (fullKey: string, settingKey: string, value: unknown) => {
    // Parse the full key to get appId and category
    const parts = fullKey.split('.');
    if (parts.length >= 2) {
      const appId = parts[0];
      const category = parts.slice(1).join('.');
      updateAppSetting(appId, category, settingKey, value);
    }
  };

  const renderSettingControl = (fullKey: string, settingKey: string, value: unknown) => {
    // Try to determine the setting type from the value
    if (typeof value === 'boolean') {
      // Boolean setting
      return (
        <ToggleSwitch
          enabled={value}
          onChange={(enabled) => handleAppSettingChange(fullKey, settingKey, enabled)}
        />
      );
    } else if (typeof value === 'number') {
      // Numeric setting - treat as slider with reasonable defaults
      const max = Math.max(100, value * 2); // Reasonable max
      return (
        <div className="setting-control">
          <Slider
            value={value}
            min={0}
            max={max}
            onChange={(newValue) => handleAppSettingChange(fullKey, settingKey, newValue)}
          />
          <span className="status-display">{value}</span>
        </div>
      );
    } else {
      // String setting or unknown - just display for now
      return (
        <span className="status-display" style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {String(value)}
        </span>
      );
    }
  };

  return (
    <div className="settings-section">
      <h2>App Settings</h2>
      
      {appSettingsEntries.map(([fullKey, settings]) => {
        const appName = fullKey.split('.')[0];
        const categoryName = fullKey.split('.').slice(1).join('.');
        
        return (
          <div key={fullKey} style={{ marginBottom: '20px' }}>
            <h3 style={{ 
              color: '#34495e', 
              fontSize: '1rem', 
              marginBottom: '12px',
              borderBottom: '1px solid #ecf0f1',
              paddingBottom: '4px'
            }}>
              {appName} - {categoryName}
            </h3>
            
            {Object.entries(settings).map(([settingKey, value]) => (
              <div key={settingKey} className="setting-item">
                <span className="setting-label">{settingKey}</span>
                <div className="setting-control">
                  {renderSettingControl(fullKey, settingKey, value)}
                </div>
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
};

export default AppSettings;