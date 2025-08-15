package com.penumbraos.bridge.callback;

interface ISettingsCallback {
    void onSettingChanged(String appId, String category, String key, String value);
    void onSettingsRegistered(String appId, String category);
    void onError(String message);
    void onActionResult(String appId, String action, boolean success, String message, in Map data);
}