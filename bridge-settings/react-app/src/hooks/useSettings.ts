import { useState, useEffect } from "react";
import { useWebSocketMessages, useWebSocket } from "./useWebSocket";
import { SystemStatus, ActionResult, ExecutionStatus } from "../types/settings";

export function useSettings() {
  const [allSettings, setAllSettings] = useState<
    Record<string, Record<string, unknown>>
  >({});
  const [systemStatus, setSystemStatus] = useState<SystemStatus>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionResults, setActionResults] = useState<
    Record<string, ActionResult>
  >({});
  const [executionStatus, setExecutionStatus] =
    useState<ExecutionStatus | null>(null);

  const { lastMessage } = useWebSocketMessages();
  const { updateSetting, executeAction, connectionState } = useWebSocket();

  useEffect(() => {
    if (!lastMessage) return;

    switch (lastMessage.type) {
      case "allSettings":
        const { executionStatus, ...settings } = lastMessage.settings;
        setAllSettings(settings);
        setExecutionStatus(
          executionStatus
            ? (executionStatus as unknown as ExecutionStatus)
            : null
        );
        setLoading(false);
        break;

      case "settingChanged":
        setAllSettings((prev) => ({
          ...prev,
          [lastMessage.category]: {
            ...prev[lastMessage.category],
            [lastMessage.key]: lastMessage.value,
          },
        }));
        break;

      case "statusUpdate":
        if (lastMessage.statusType === "battery") {
          setSystemStatus((prev) => ({
            ...prev,
            battery: {
              level: Number(lastMessage.data.level) || 0,
              charging: Boolean(lastMessage.data.charging),
              powerSaveMode: Boolean(lastMessage.data.powerSaveMode),
            },
          }));
        } else if (lastMessage.statusType === "display") {
          setSystemStatus((prev) => ({
            ...prev,
            display: {
              brightness: Number(lastMessage.data.brightness) || 50,
              autoBrightness: Boolean(lastMessage.data.autoBrightness),
            },
          }));
        } else if (lastMessage.statusType === "audio") {
          setSystemStatus((prev) => ({
            ...prev,
            audio: {
              volume: Number(lastMessage.data.volume) || 50,
              muted: Boolean(lastMessage.data.muted),
            },
          }));
        } else if (lastMessage.statusType === "network") {
          setSystemStatus((prev) => ({
            ...prev,
            network: {
              wifiEnabled: Boolean(lastMessage.data.wifiEnabled),
            },
          }));
        }
        break;

      case "actionResult":
        console.log(
          `Action result for ${lastMessage.appId}.${lastMessage.action}:`,
          lastMessage
        );
        const actionKey = `${lastMessage.appId}.${lastMessage.action}`;
        setActionResults((prev) => ({
          ...prev,
          [actionKey]: {
            success: lastMessage.success,
            message: lastMessage.message,
            data: lastMessage.data,
            logs: lastMessage.logs,
          },
        }));
        break;

      case "appEvent":
        console.log(`App event from ${lastMessage.appId}:`, lastMessage);
        if (lastMessage.eventType === "actionResult" && lastMessage.payload) {
          const payload = lastMessage.payload as any;
          const actionKey = `${lastMessage.appId}.${payload.action}`;
          setActionResults((prev) => ({
            ...prev,
            [actionKey]: {
              success: payload.success,
              message: payload.message,
              data: payload.data,
              logs: Array.isArray(payload.logs) ? payload.logs : [],
            },
          }));
        }
        break;

      case "actionsRegistered":
        console.log(
          `Actions registered for ${lastMessage.appId}:`,
          lastMessage.actions
        );
        // Action definitions could be stored globally for dynamic UI generation
        break;

      case "error":
        setError(lastMessage.message);
        break;
    }
  }, [lastMessage]);

  const updateSystemSetting = (key: string, value: unknown) => {
    updateSetting("system", key, value);
  };

  const updateAppSetting = (
    appId: string,
    category: string,
    key: string,
    value: unknown
  ) => {
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
    actionResults,
    executionStatus,
    updateSystemSetting,
    updateAppSetting,
    getSystemSettings,
    getAppSettings,
    executeAction,
  };
}
