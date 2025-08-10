package com.penumbraos.bridge;

import com.penumbraos.bridge.ISettingsCallback;
import com.penumbraos.bridge.types.SystemSettingInfo;
import com.penumbraos.bridge.types.ActionDefinition;
import com.penumbraos.bridge.types.AppActionInfo;

interface ISettingsProvider {
    void registerSettingsCategory(String appId, String category, in Map settingsSchema, ISettingsCallback callback);
    void unregisterSettingsCategory(String appId, String category);
    void updateSetting(String appId, String category, String key, String value);
    String getSetting(String appId, String category, String key);
    Map getAllSettings(String appId);
    Map getSystemSettings();
    void updateSystemSetting(String key, String value);
    void executeAction(String appId, String action, in Map params);
    void executeActionWithCallback(String appId, String action, in Map params, ISettingsCallback callback);
    void sendAppStatusUpdate(String appId, String component, in Map payload);
    void sendAppEvent(String appId, String eventType, in Map payload);
    
    // Discovery methods for dynamic registration
    List<SystemSettingInfo> getAvailableSystemSettings();
    List<String> getRegisteredApps();
    List<ActionDefinition> getAppActions(String appId);
    List<AppActionInfo> getAllAvailableActions();
}