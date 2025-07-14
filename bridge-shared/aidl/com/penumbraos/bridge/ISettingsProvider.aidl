package com.penumbraos.bridge;

import com.penumbraos.bridge.ISettingsCallback;

interface ISettingsProvider {
    void registerSettingsCategory(String appId, String category, in Map settingsSchema, ISettingsCallback callback);
    void unregisterSettingsCategory(String appId, String category);
    void updateSetting(String appId, String category, String key, String value);
    String getSetting(String appId, String category, String key);
    Map getAllSettings(String appId);
    Map getSystemSettings();
    void updateSystemSetting(String key, String value);
    void sendAppStatusUpdate(String appId, String component, in Map payload);
    void sendAppEvent(String appId, String eventType, in Map payload);
}