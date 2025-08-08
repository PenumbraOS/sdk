import { chooseLiveClasses } from "./utils/classloader";
import { setupNetworkHooks, setDnsProvider } from "./hooks/network";
import { setupCliCallbackHooks } from "./hooks/cli-callbacks";

const initialize = (): void => {
  setupNetworkHooks();
  setupCliCallbackHooks();

  const dnsProviderInstances = chooseLiveClasses(
    "com.penumbraos.bridge_system.provider.DnsProvider"
  );
  if (dnsProviderInstances.length > 0) {
    setDnsProvider(dnsProviderInstances[0]);
  }
};

initialize();
