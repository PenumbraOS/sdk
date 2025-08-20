import { loadClass } from "../utils/classloader";
import { createFillStoreMetadataRequestImplementation } from "../utils/lpa-parser";

export const setupLpaByteHooks = (): void => {
  console.log("[Frida] Setting up LPA byte twiddling hooks...");

  try {
    const FillerEngine = loadClass(
      "es.com.valid.lib_lpa.controler.FillerEngine"
    );

    if (FillerEngine?.fillStoreMetadataRequest) {
      FillerEngine.fillStoreMetadataRequest.implementation =
        createFillStoreMetadataRequestImplementation();
      console.log(
        "[Frida] Patched FillerEngine.fillStoreMetadataRequest() with corrected BF25 parsing logic"
      );
    } else {
      console.error(
        "[Frida] Could not find FillerEngine.fillStoreMetadataRequest to patch"
      );
    }
  } catch (err) {
    console.error("[Frida] Error hooking FillerEngine: " + err);
  }
};
