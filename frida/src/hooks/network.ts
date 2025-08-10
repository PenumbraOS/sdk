import { log } from "../utils/logging";

let dnsProvider: any = null;

// Track getDns calls to prevent infinite recursion
let insideDnsCall = false;

export const setupNetworkHooks = (): void => {
  try {
    const InetAddress = Java.use("java.net.InetAddress");

    const getDns = (hostname: string): string => {
      if (insideDnsCall) {
        log(`Skipping ${hostname} fetch due to inside DNS call`);
        return hostname;
      }

      insideDnsCall = true;
      const result = dnsProvider.lookup
        .overload("java.lang.String")
        .call(dnsProvider, hostname);
      insideDnsCall = false;
      return result;
    };

    const hookDnsMethod = (method: any, isList: boolean) => {
      const original = method.implementation;
      method.implementation = function (hostname: string) {
        if (dnsProvider) {
          const resolvedHostname = getDns(hostname);
          if (resolvedHostname === null) {
            const UnknownHostException = Java.use(
              "java.net.UnknownHostException"
            );
            throw UnknownHostException.$new(
              `DNS resolution failed for ${hostname}`
            );
          } else if (isIpAddress(resolvedHostname)) {
            log(`Creating InetAddress directly for IP ${resolvedHostname}`);
            const parts = resolvedHostname.split(".");
            const bytes = Java.array(
              "byte",
              parts.map((p) => parseInt(p))
            );
            const result = InetAddress.getByAddress
              .overload("[B")
              .call(InetAddress, bytes);
            return isList
              ? Java.array("java.net.InetAddress", [result])
              : result;
          }
          return original.call(this, resolvedHostname);
        }
        return original.call(this, hostname);
      };
    };

    hookDnsMethod(InetAddress.getByName.overload("java.lang.String"), false);
    hookDnsMethod(InetAddress.getAllByName.overload("java.lang.String"), true);
  } catch (err) {
    log(`[Frida] Network hook error: ${err}`);
  }
};

export const setDnsProvider = (provider: any): void => {
  dnsProvider = provider;
};

const isIpAddress = (str: string): boolean => {
  const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
  return ipv4Regex.test(str);
};
