import { log } from "../utils/logging";

let penumbraClient: any = null;

export const setupNetworkHooks = (): void => {
  try {
    const InetAddress = Java.use("java.net.InetAddress");

    const getDns = (hostname: string): string => {
      return penumbraClient.dns.value.lookup
        .overload("java.lang.String")
        .call(penumbraClient.dns.value, hostname);
    };

    const hookDnsMethod = (method: any) => {
      const original = method.implementation;
      method.implementation = function (hostname: string) {
        if (penumbraClient) {
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

export const setPenumbraClient = (client: any): void => {
  penumbraClient = client;
};
