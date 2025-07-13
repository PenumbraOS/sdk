package com.penumbraos.bridge;

interface ISettingsCallback {
    void onSettingChanged(String appId, String category, String key, String value);
    void onSettingsRegistered(String appId, String category);
    void onError(String message);
}