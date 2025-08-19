export type SettingsMessage =
  | {
      type: "updateSetting";
      appId: string;
      category: string;
      key: string;
      value: unknown;
    }
  | { type: "registerForUpdates"; categories: string[] }
  | { type: "getAllSettings" }
  | {
      type: "executeAction";
      appId: string;
      action: string;
      params: Record<string, unknown>;
    };

export type StatusMessage =
  | { type: "settingChanged"; category: string; key: string; value: unknown }
  | { type: "statusUpdate"; statusType: string; data: Record<string, unknown> }
  | { type: "allSettings"; settings: Record<string, Record<string, unknown>> }
  | {
      type: "appStatusUpdate";
      appId: string;
      component: string;
      data: Record<string, unknown>;
    }
  | {
      type: "appEvent";
      appId: string;
      eventType: string;
      payload: Record<string, unknown>;
    }
  | {
      type: "actionResult";
      appId: string;
      action: string;
      success: boolean;
      message?: string;
      data?: Record<string, unknown>;
      logs?: LogEntry[];
    }
  | {
      type: "actionsRegistered";
      appId: string;
      actions: Record<string, ActionDefinition>;
    }
  | { type: "error"; message: string }
  | {
      type: "logEntry";
      level: string;
      tag: string;
      message: string;
      timestamp: number;
    };

export interface Setting {
  key: string;
  value: unknown;
  type: "boolean" | "integer" | "string" | "float" | "action";
  defaultValue: unknown;
  validation?: {
    min?: number;
    max?: number;
    allowedValues?: string[];
    regex?: string;
  };
}

export interface ActionDefinition {
  key: string;
  displayText: string;
  parameters?: ActionParameter[];
  description?: string;
}

export interface ActionParameter {
  name: string;
  type: "boolean" | "integer" | "string" | "float";
  required: boolean;
  defaultValue?: unknown;
  description?: string;
}

export interface LogEntry {
  timestamp: number;
  level: "INFO" | "WARNING" | "ERROR" | "DEBUG";
  message: string;
}

export interface ActionResult {
  success: boolean;
  message?: string;
  data?: Record<string, unknown>;
  logs?: LogEntry[];
}

export interface ExecutionStatus {
  providerId: string;
  actionName: string;
  params: Record<string, unknown>;
  startTime: number;
  duration: number;
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
