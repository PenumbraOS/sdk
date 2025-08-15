package com.penumbraos.bridge;

import com.penumbraos.bridge.callback.IEsimCallback;

interface IEsimProvider {
    void getProfiles(IEsimCallback callback);
    void getActiveProfile(IEsimCallback callback);
    void getActiveProfileIccid(IEsimCallback callback);
    void getEid(IEsimCallback callback);
    void enableProfile(String iccid, IEsimCallback callback);
    void disableProfile(String iccid, IEsimCallback callback);
    void deleteProfile(String iccid, IEsimCallback callback);
    void setNickname(String iccid, String nickname, IEsimCallback callback);
    void downloadProfile(String activationCode, IEsimCallback callback);
    void downloadAndEnableProfile(String activationCode, IEsimCallback callback);
    void downloadVerifyAndEnableProfile(String activationCode, IEsimCallback callback);
}
