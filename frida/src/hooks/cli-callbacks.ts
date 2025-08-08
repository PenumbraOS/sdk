import { log } from "../utils/logging";
import { loadClass } from "../utils/classloader";

export const setupCliCallbackHooks = (): void => {
  setupFactoryServiceHooks();
  setupProfileInfoControllerListenerHooks();
  setupDownloadControllerListenerHooks();
  setupEuiccLevelControllerListenerHooks();
};

const notifyJavaCallback = (
  operationType: string,
  operationName: string,
  result: string,
  isError: boolean
): void => {
  Java.scheduleOnMainThread(() => {
    try {
      const MockFactoryService = loadClass(
        "com.penumbraos.bridge_system.esim.MockFactoryService"
      );
      const instance = MockFactoryService.fridaCallbackInstance.value;
      if (instance) {
        instance.onFridaCallback(
          operationType,
          operationName,
          result || "",
          isError
        );
      }
    } catch (e) {
      log(`[Frida] Callback error: ${e}`);
    }
  });
};

let lastCapturedProfiles: any[] = [];
let lastCapturedEid: string = "";

const safeHexToAscii = (hexString: any): string => {
  try {
    if (!hexString?.getValue) {
      return "";
    }
    const hexValue = hexString.getValue();
    if (!hexValue) {
      return "";
    }
    const Util = Java.use("es.com.valid.lib_lpa.common.Util");
    return Util.HexToAscII(hexValue).toString();
  } catch (e) {
    return "";
  }
};

const convertProfileListToStructuredData = (profileList: any): any[] => {
  try {
    const profiles = [];
    const profileArray = Java.cast(
      profileList,
      Java.use("java.util.ArrayList")
    );
    const ProfileInfoClass = Java.use(
      "es.com.valid.lib_lpa.dataClasses.ProfileInfo"
    );

    for (let i = 0; i < profileArray.size(); i++) {
      try {
        const profileInfo = Java.cast(profileArray.get(i), ProfileInfoClass);

        const getField = (getter: () => any, fallback = "") => {
          try {
            return getter();
          } catch {
            return fallback;
          }
        };

        const iccid = getField(() =>
          profileInfo.getIccid()?.getValueRotated()?.toString()
        );
        const profileState = getField(() =>
          profileInfo.getProfileState()?.getValueString()?.toString()
        );
        const profileName = getField(() =>
          safeHexToAscii(profileInfo.getProfileName())
        );
        const profileNickname = getField(() =>
          safeHexToAscii(profileInfo.getProfileNickname())
        );
        const serviceProviderName = getField(() =>
          safeHexToAscii(profileInfo.getServiceProviderName())
        );

        profiles.push({
          iccid,
          profileState,
          profileName,
          profileNickname,
          serviceProviderName,
          index: i,
          isEnabled: profileState.toLowerCase() === "enabled",
          isDisabled: profileState.toLowerCase() === "disabled",
        });
      } catch (e) {
        profiles.push({
          index: i,
          error: (e as any).toString(),
          iccid: "",
          profileState: "unknown",
        });
      }
    }
    return profiles;
  } catch (e) {
    return [];
  }
};

const setupFactoryServiceHooks = (): void => {
  try {
    const CommunicationManager = loadClass(
      "es.com.valid.lib_lpa.cardCommunication.CommunicationManager"
    );
    if (CommunicationManager?.getProfileListAsArray) {
      CommunicationManager.getProfileListAsArray.implementation = function () {
        const profileList = this.getProfileListAsArray();
        if (profileList) {
          lastCapturedProfiles =
            convertProfileListToStructuredData(profileList);
        }
        return profileList;
      };
    }

    const factoryService = loadClass(
      "humane.connectivity.esimlpa.factoryService"
    );
    const setSysProp = factoryService?.setSysProp?.overload(
      "java.lang.String",
      "java.lang.String"
    );
    if (setSysProp) {
      setSysProp.implementation = function (key: string, value: string) {
        const result = this.setSysProp(key, value);

        if (key === "humane.esim.lastintent.result") {
          const isError =
            value.includes("Error") ||
            value.includes("No ") ||
            value.includes("Couldn't");
          const operationType = "factoryService";
          let operationName = "setSysProp";
          let resultData = value;

          if (value.includes("getProfile")) {
            operationName = "getProfiles";
            resultData =
              !isError && lastCapturedProfiles.length > 0
                ? JSON.stringify(lastCapturedProfiles)
                : value;
          } else if (value.includes("Get EID")) {
            operationName = "getEid";
            resultData =
              !isError && lastCapturedEid
                ? JSON.stringify(lastCapturedEid)
                : value;
          } else if (value.includes("Get Ative profile")) {
            operationName = "getActiveProfile";
            const activeProfile = lastCapturedProfiles.find((p) => p.isEnabled);
            resultData =
              !isError && activeProfile ? JSON.stringify(activeProfile) : value;
          } else if (value.includes("ICCID")) {
            operationName = "getActiveProfileIccid";
            const activeProfile = lastCapturedProfiles.find((p) => p.isEnabled);
            resultData =
              !isError && activeProfile
                ? JSON.stringify(activeProfile.iccid)
                : value;
          }

          notifyJavaCallback(operationType, operationName, resultData, isError);
        }

        return result;
      };
    }
  } catch (err) {
    log(`[Frida] Factory service hook error: ${err}`);
  }
};

const setupProfileInfoControllerListenerHooks = (): void => {
  try {
    const ProfileInfoControlerListener = loadClass(
      "es.com.valid.lib_lpa.controler.ProfileInfoControler$ProfileInfoControlerListener"
    );
    if (!ProfileInfoControlerListener) {
      return;
    }

    const hookMethod = (methodName: string, operation: string) => {
      if (ProfileInfoControlerListener[methodName]) {
        ProfileInfoControlerListener[methodName].implementation = function (
          result: string
        ) {
          const returnValue = this[methodName](result);
          const operationResult = {
            operation,
            result,
            success: methodName !== "onError",
          };
          notifyJavaCallback(
            "ProfileInfoControler",
            methodName,
            JSON.stringify(operationResult),
            methodName === "onError"
          );
          return returnValue;
        };
      }
    };

    hookMethod("onEnable", "enable");
    hookMethod("onDisable", "disable");
    hookMethod("onDelete", "delete");
    hookMethod("onsetNickName", "setNickname");
    hookMethod("onError", "error");
  } catch (err) {
    log(`[Frida] Profile controller hook error: ${err}`);
  }
};

const setupDownloadControllerListenerHooks = (): void => {
  try {
    const DownloadControlerListener = loadClass(
      "es.com.valid.lib_lpa.controler.DownloadControler$DownloadControlerListener"
    );
    if (!DownloadControlerListener) return;

    if (DownloadControlerListener.onFinished) {
      DownloadControlerListener.onFinished.implementation = function (
        result: string
      ) {
        const returnValue = this.onFinished(result);
        const downloadResult = { operation: "download", result, success: true };
        notifyJavaCallback(
          "DownloadControler",
          "onFinished",
          JSON.stringify(downloadResult),
          false
        );
        return returnValue;
      };
    }

    if (DownloadControlerListener.onError) {
      DownloadControlerListener.onError.implementation = function (
        result: string
      ) {
        const returnValue = this.onError(result);
        const downloadResult = {
          operation: "download",
          result,
          success: false,
        };
        notifyJavaCallback(
          "DownloadControler",
          "onError",
          JSON.stringify(downloadResult),
          true
        );
        return returnValue;
      };
    }
  } catch (err) {
    log(`[Frida] Download controller hook error: ${err}`);
  }
};

const setupEuiccLevelControllerListenerHooks = (): void => {
  try {
    const EuiccLevelControllerListener = loadClass(
      "es.com.valid.lib_lpa.controler.EuiccLevelController$EuiccLevelControllerListener"
    );
    if (!EuiccLevelControllerListener) return;

    const hookEuiccMethod = (methodName: string) => {
      if (EuiccLevelControllerListener[methodName]) {
        EuiccLevelControllerListener[methodName].implementation = function (
          result: string
        ) {
          if (methodName === "onGetEid") lastCapturedEid = result;
          const returnValue = this[methodName](result);
          const resultData =
            methodName === "onGetEid" ? JSON.stringify(result) : result;
          notifyJavaCallback(
            "EuiccLevelController",
            methodName,
            resultData,
            methodName === "onError"
          );
          return returnValue;
        };
      }
    };

    hookEuiccMethod("onGetEid");
    hookEuiccMethod("onMemoryReset");
    hookEuiccMethod("onTestMemoryReset");
    hookEuiccMethod("onSetDefaultSMDPPlus");
    hookEuiccMethod("onError");
  } catch (err) {
    log(`[Frida] EUICC controller hook error: ${err}`);
  }
};
