package com.penumbraos.bridge.callback;

import com.penumbraos.bridge.types.EsimProfile;
import com.penumbraos.bridge.types.EsimOperationResult;

interface IEsimCallback {
    oneway void onProfiles(in List<EsimProfile> profiles);
    oneway void onActiveProfile(in EsimProfile profile);
    oneway void onActiveProfileIccid(String iccid);
    oneway void onEid(String eid);
    oneway void onOperationResult(in EsimOperationResult result);
    oneway void onError(String error);
}