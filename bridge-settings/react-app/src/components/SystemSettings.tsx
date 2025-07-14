import React from 'react';
import { useSettings } from '../hooks/useSettings';
import ToggleSwitch from './ToggleSwitch';
import Slider from './Slider';

const SystemSettings: React.FC = () => {
  const { getSystemSettings, updateSystemSetting } = useSettings();
  const systemSettings = getSystemSettings();

  const handleToggle = (key: string, enabled: boolean) => {
    updateSystemSetting(key, enabled);
  };

  const handleSlider = (key: string, value: number) => {
    updateSystemSetting(key, value);
  };

  return (
    <div className="settings-section">
      <h2>System Settings</h2>
      
      <div className="setting-item">
        <span className="setting-label">Display Enabled</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={Boolean(systemSettings['display.humane_enabled'])}
            onChange={(enabled) => handleToggle('display.humane_enabled', enabled)}
          />
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Audio Volume</span>
        <div className="setting-control">
          <Slider
            value={Number(systemSettings['audio.volume']) || 70}
            min={0}
            max={100}
            onChange={(value) => handleSlider('audio.volume', value)}
          />
          <span className="status-display">
            {Number(systemSettings['audio.volume']) || 70}%
          </span>
        </div>
      </div>

      <div className="setting-item">
        <span className="setting-label">Muted</span>
        <div className="setting-control">
          <ToggleSwitch
            enabled={Boolean(systemSettings['audio.muted'])}
            onChange={(enabled) => handleToggle('audio.muted', enabled)}
          />
        </div>
      </div>
    </div>
  );
};

export default SystemSettings;