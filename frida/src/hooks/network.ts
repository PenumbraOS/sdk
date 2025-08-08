import { log } from "../utils/logging";

let dnsProvider: any = null;

export const setupNetworkHooks = (): void => {
  try {
    const InetAddress = Java.use("java.net.InetAddress");

    const getDns = (hostname: string): string => {
      // TODO: Something about this is incorrect
      return dnsProvider.lookup
        .overload("java.lang.String")
        .call(dnsProvider, hostname);
    };

    const hookDnsMethod = (method: any) => {
      const original = method.implementation;
      method.implementation = function (hostname: string) {
        if (dnsProvider) {
          const resolvedHostname = getDns(hostname);
          return original.call(this, resolvedHostname);
        }
        return original.call(this, hostname);
      };
    };

    hookDnsMethod(InetAddress.getByName.overload("java.lang.String"));
    hookDnsMethod(InetAddress.getAllByName.overload("java.lang.String"));
  } catch (err) {
    log(`[Frida] Network hook error: ${err}`);
  }
};

export const setDnsProvider = (provider: any): void => {
  dnsProvider = provider;
};
