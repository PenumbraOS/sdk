import { useState, useEffect } from 'react';
import { useWebSocketMessages, useWebSocket } from './useWebSocket';
import { SystemStatus } from '../types/settings';

export function useSettings() {
  const [allSettings, setAllSettings] = useState<Record<string, Record<string, unknown>>>({});
  const [systemStatus, setSystemStatus] = useState<SystemStatus>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const { lastMessage } = useWebSocketMessages();
  const { updateSetting, connectionState } = useWebSocket();

  useEffect(() => {
    if (!lastMessage) return;

    switch (lastMessage.type) {
      case 'allSettings':
        setAllSettings(lastMessage.settings);
        setLoading(false);
        break;
        
      case 'settingChanged':
        setAllSettings(prev => ({
          ...prev,
          [lastMessage.category]: {
            ...prev[lastMessage.category],
            [lastMessage.key]: lastMessage.value
          }
        }));
        break;
        
      case 'statusUpdate':
        if (lastMessage.statusType === 'battery') {
          setSystemStatus(prev => ({
            ...prev,
            battery: {
              level: Number(lastMessage.data.level) || 0,
              charging: Boolean(lastMessage.data.charging),
              powerSaveMode: Boolean(lastMessage.data.powerSaveMode)
            }
          }));
        } else if (lastMessage.statusType === 'display') {
          setSystemStatus(prev => ({
            ...prev,
            display: {
              brightness: Number(lastMessage.data.brightness) || 50,
              autoBrightness: Boolean(lastMessage.data.autoBrightness)
            }
          }));
        } else if (lastMessage.statusType === 'audio') {
          setSystemStatus(prev => ({
            ...prev,
            audio: {
              volume: Number(lastMessage.data.volume) || 50,
              muted: Boolean(lastMessage.data.muted)
            }
          }));
        } else if (lastMessage.statusType === 'network') {
          setSystemStatus(prev => ({
            ...prev,
            network: {
              wifiEnabled: Boolean(lastMessage.data.wifiEnabled)
            }
          }));
        }
        break;
        
      case 'error':
        setError(lastMessage.message);
        break;
    }
  }, [lastMessage]);

  const updateSystemSetting = (key: string, value: unknown) => {
    updateSetting('system', key, value);
  };

  const updateAppSetting = (appId: string, category: string, key: string, value: unknown) => {
    updateSetting(`${appId}.${category}`, key, value);
  };

  const getSystemSettings = () => {
    return allSettings.system || {};
  };

  const getAppSettings = (appId: string) => {
    return Object.entries(allSettings)
      .filter(([key]) => key.startsWith(`${appId}.`))
      .reduce((acc, [key, value]) => {
        const category = key.substring(appId.length + 1);
        acc[category] = value;
        return acc;
      }, {} as Record<string, Record<string, unknown>>);
  };

  return {
    allSettings,
    systemStatus,
    loading,
    error,
    connected: connectionState.connected,
    updateSystemSetting,
    updateAppSetting,
    getSystemSettings,
    getAppSettings
  };
}