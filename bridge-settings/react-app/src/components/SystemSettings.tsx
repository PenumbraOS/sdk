import React from 'react';
import { useSettings } from '../hooks/useSettings';
import ToggleSwitch from './ToggleSwitch';
import Slider from './Slider';

const SystemSettings: React.FC = () => {
  const { getSystemSettings, updateSystemSetting } = useSettings();
  const systemSettings = getSystemSettings();

  const handleToggle = (key: string, enabled: boolean) => {
    updateSystemSetting(key, enabled.toString());
  };

  const handleSlider = (key: string, value: number) => {
    updateSystemSetting(key, value.toString());
  };

  return (
    <div className="settings-section">
      <h2>System Settings</h2>
      
      <div className="setting-item">
        <span className="setting-label">Display Brightness</span>
        <div className="setting-control">
          <Slider
            value={parseInt(systemSettings['display.brightness'] || '50')}
            min={0}
            max={100}
            onChange={(value) => handleSlider('display.brightness', value)}
          />
          <span className="status-display">
            {systemSettings['display.brightness'] || '50'}%
          </span>
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Auto Brightness</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={systemSettings['display.auto_brightness'] === 'true'}
            onChange={(enabled) => handleToggle('display.auto_brightness', enabled)}
          />
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Audio Volume</span>
        <div className="setting-control">
          <Slider
            value={parseInt(systemSettings['audio.volume'] || '70')}
            min={0}
            max={100}
            onChange={(value) => handleSlider('audio.volume', value)}
          />
          <span className="status-display">
            {systemSettings['audio.volume'] || '70'}%
          </span>
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Muted</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={systemSettings['audio.muted'] === 'true'}
            onChange={(enabled) => handleToggle('audio.muted', enabled)}
          />
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">WiFi Enabled</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={systemSettings['network.wifi_enabled'] === 'true'}
            onChange={(enabled) => handleToggle('network.wifi_enabled', enabled)}
          />
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Power Save Mode</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={systemSettings['battery.power_save_mode'] === 'true'}
            onChange={(enabled) => handleToggle('battery.power_save_mode', enabled)}
          />
        </div>
      </div>
    </div>
  );
};

export default SystemSettings;