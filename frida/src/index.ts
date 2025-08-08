import { chooseLiveClasses } from "./utils/classloader";
import { setupNetworkHooks, setPenumbraClient } from "./hooks/network";
import { setupCliCallbackHooks } from "./hooks/cli-callbacks";

const initialize = (): void => {
  setupNetworkHooks();
  setupCliCallbackHooks();

  const penumbraClientInstances = chooseLiveClasses(
    "com.penumbraos.sdk.PenumbraClient"
  );
  if (penumbraClientInstances.length > 0) {
    setPenumbraClient(penumbraClientInstances[0]);
  }
};

initialize();
