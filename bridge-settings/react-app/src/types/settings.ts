// Message types for WebSocket communication with Kotlin backend

export type SettingsMessage = 
  | { type: 'updateSetting'; category: string; key: string; value: string }
  | { type: 'registerForUpdates'; categories: string[] }
  | { type: 'getAllSettings' };

export type StatusMessage = 
  | { type: 'settingChanged'; category: string; key: string; value: string }
  | { type: 'statusUpdate'; statusType: string; data: Record<string, string> }
  | { type: 'allSettings'; settings: Record<string, Record<string, string>> }
  | { type: 'error'; message: string };

export interface Setting {
  key: string;
  value: string;
  type: 'boolean' | 'integer' | 'string' | 'float';
  defaultValue: string;
  validation?: {
    min?: number;
    max?: number;
    allowedValues?: string[];
    regex?: string;
  };
}

export interface SettingsCategory {
  name: string;
  settings: Record<string, Setting>;
}

export interface AppSettings {
  appId: string;
  categories: Record<string, SettingsCategory>;
}

export interface SystemStatus {
  battery?: {
    level: number;
    charging: boolean;
    powerSaveMode: boolean;
  };
  display?: {
    brightness: number;
    autoBrightness: boolean;
  };
  audio?: {
    volume: number;
    muted: boolean;
  };
  network?: {
    wifiEnabled: boolean;
  };
}

export interface ConnectionState {
  connected: boolean;
  connecting: boolean;
  error: string | null;
}