// Frida Script to Debug LPA Flow (with ClassLoader handling)
const android_log_write = new NativeFunction(
  Module.getExportByName(null, "__android_log_write"),
  "int",
  ["int", "pointer", "pointer"]
);

const log = (value) => {
  const tag = Memory.allocUtf8String("Frida");
  const string = Memory.allocUtf8String(value);
  android_log_write(3, tag, string);
};

log(
  "[Frida] Starting comprehensive LPA debug script (v2 with ClassLoader handling)..."
);

let penumbraClient = null;

// --- Helper Functions ---
function bytesToHex(bytes) {
  if (!bytes) return null;
  return Array.from(bytes, function (byteValue) {
    return ("0" + (byteValue & 0xff).toString(16)).slice(-2);
  }).join("");
}

function now() {
  return new Date().getTime();
}

function logArgs(args) {
  if (!args || args.length === 0) return "no arguments";
  return Array.from(args)
    .map(function (arg) {
      if (arg === null) return "null";
      if (arg === undefined) return "undefined";
      try {
        let str = arg.toString();
        if (str.startsWith("[B@") && arg.length && typeof arg[0] === "number") {
          // Basic check for byte array
          return "byte[] (hex): " + bytesToHex(arg);
        }
        if (str.length > 100) str = str.substring(0, 97) + "..."; // Truncate long strings
        return str;
      } catch (e) {
        return "Error converting arg";
      }
    })
    .join(", ");
}

const classLoaders = Java.enumerateClassLoadersSync().map(
  Java.ClassFactory.get
);

function loadClass(name) {
  for (const classLoader of classLoaders) {
    try {
      return classLoader.use(name);
    } catch {}
  }

  return undefined;
}

function chooseLiveClasses(name) {
  const instances = [];
  for (const classLoader of classLoaders) {
    try {
      classLoader.choose(name, {
        onMatch: (instance) => {
          instances.push(instance);
        },
      });
    } catch {}
  }

  return instances;
}

// --- Main Hooking Logic (to be called once ClassLoader is found) ---
function applyAllHooks() {
  log("[Frida] Applying all hooks with ClassLoader");

  // --- Section 1: DownloadControler - Mutual Authentication & Parsing ---
  log("\n[Frida] Setting up hooks for Mutual Authentication & Parsing...");
  try {
    const DownloadControler = loadClass(
      "es.com.valid.lib_lpa.controler.DownloadControler"
    );
    const mutualAuthOverload =
      DownloadControler.mutualAuthentication.overload();
    if (mutualAuthOverload) {
      mutualAuthOverload.implementation = function () {
        log("[Frida] ==> DC.mutualAuthentication() entered");
        const startTime = now();
        let result;
        try {
          result = mutualAuthOverload.call(this);
        } catch (e) {
          log(
            "ERROR: [Frida] <== DC.mutualAuthentication() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <== DC.mutualAuthentication() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            result
        );
        return result;
      };
      log("[Frida] Hooked DC.mutualAuthentication()");
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking DC.mutualAuthentication: " + err);
  }

  try {
    const AuthClientResponse = loadClass(
      "es.com.valid.lib_lpa.dataClasses.AuthenticateClientResponseEs9"
    );
    if (AuthClientResponse.getProfileMetadata) {
      AuthClientResponse.getProfileMetadata.implementation = function () {
        log("[Frida] ----> AuthResp.getProfileMetadata()");
        const res = this.getProfileMetadata();
        log("[Frida]       ProfileMetadata (hex): " + bytesToHex(res));
        return res;
      };
    }
    if (AuthClientResponse.getSmdpSigned2) {
      AuthClientResponse.getSmdpSigned2.implementation = function () {
        log("[Frida] ----> AuthResp.getSmdpSigned2()");
        const res = this.getSmdpSigned2();
        log("[Frida]       SmdpSigned2 (hex): " + bytesToHex(res));
        return res;
      };
    }
    log("[Frida] Hooked AuthenticateClientResponseEs9 getters");
  } catch (err) {
    log("ERROR: [Frida] Error hooking AuthClientResponseEs9: " + err);
  }

  try {
    const FillerEngine = loadClass(
      "es.com.valid.lib_lpa.controler.FillerEngine"
    );
    const ControlerUtil = loadClass("es.com.valid.lib_lpa.controler.Util"); // Load the correct Util class
    const StoreMetadataRequestClass = loadClass(
      "es.com.valid.lib_lpa.dataClasses.StoreMetadataRequest"
    );

    if (
      FillerEngine.fillStoreMetadataRequest &&
      ControlerUtil && // Check for the correct Util
      StoreMetadataRequestClass
    ) {
      FillerEngine.fillStoreMetadataRequest.implementation = function (str, i) {
        log(
          "[Frida] ----> FE.fillStoreMetadataRequest (PATCHED v2) entered with str_len:" +
            (str ? str.length : "null") +
            ", initial_offset_i:" +
            i
        );
        const startTime = now();
        let storeMetadataRequest = StoreMetadataRequestClass.$new();

        if (!str || str.length < 4) {
          log("ERROR: [Frida PATCH v2] Input string is too short.");
          throw new Error("Input string too short for BF25 processing.");
        }

        let topLevelTag = str.substring(i, i + 4).toUpperCase();
        if (topLevelTag !== "BF25") {
          log(
            "ERROR: [Frida PATCH v2] Expected BF25 tag at offset " +
              i +
              ", got: " +
              topLevelTag +
              ". Calling original."
          );
          return this.constructor.prototype.fillStoreMetadataRequest.call(
            this,
            str,
            i
          );
        }

        let headerLenNibbles = 4;
        // Use the correctly loaded ControlerUtil
        let lengthOfBF25Bytes = ControlerUtil.getBERLengthInInt(
          str,
          i + headerLenNibbles
        );
        let lengthFieldSizeNibbles = ControlerUtil.getBERLengthSizeInNibbles(
          str,
          i + headerLenNibbles
        );

        let dataStartOffsetInNibbles =
          i + headerLenNibbles + lengthFieldSizeNibbles;
        let endOfBF25DataNibbles =
          dataStartOffsetInNibbles + lengthOfBF25Bytes * 2;

        log(
          "[Frida PATCH] BF25 tag found. Declared length: " +
            lengthOfBF25Bytes +
            " bytes. Data starts at nibble-offset: " +
            dataStartOffsetInNibbles +
            ", ends at nibble-offset: " +
            endOfBF25DataNibbles +
            " (relative to start of string)."
        );

        let currentParseOffsetNibbles = dataStartOffsetInNibbles; // Current parsing offset within BF25 data content

        let iterationCount = 0;
        const MAX_ITERATIONS = 200; // Safety break

        while (
          currentParseOffsetNibbles < endOfBF25DataNibbles &&
          iterationCount < MAX_ITERATIONS
        ) {
          iterationCount++;
          if (currentParseOffsetNibbles + 2 > str.length) {
            log(
              "ERROR: [Frida PATCH v2] Offset " +
                currentParseOffsetNibbles +
                " out of bounds for reading tag (str.length " +
                str.length +
                ")"
            );
            break;
          }
          let currentTag = str
            .substring(currentParseOffsetNibbles, currentParseOffsetNibbles + 2)
            .toUpperCase();
          log(
            "[Frida PATCH v2] Loop: " +
              iterationCount +
              ", OffsetInBF25Data: " +
              (currentParseOffsetNibbles - dataStartOffsetInNibbles) +
              " (abs: " +
              currentParseOffsetNibbles +
              "), Tag: " +
              currentTag
          );

          let tagCompletelyConsumedNibbles = 0;

          try {
            if (currentTag === "5A") {
              let iccidObj = this.fillIccid(str, currentParseOffsetNibbles);
              tagCompletelyConsumedNibbles = iccidObj.getSize();
              storeMetadataRequest.setIccid(iccidObj);
            } else if (currentTag === "91") {
              let hexStrObj = this.fillHexString(
                str,
                currentParseOffsetNibbles
              );
              tagCompletelyConsumedNibbles = hexStrObj.getSize();
              storeMetadataRequest.setServiceProviderName(hexStrObj);
            } else if (currentTag === "92") {
              let hexStrObj = this.fillHexString(
                str,
                currentParseOffsetNibbles
              );
              tagCompletelyConsumedNibbles = hexStrObj.getSize();
              storeMetadataRequest.setProfileName(hexStrObj);
            } else if (currentTag === "93") {
              let iconTypeObj = this.fillIconType(
                str,
                currentParseOffsetNibbles
              );
              tagCompletelyConsumedNibbles = iconTypeObj.getSize();
              storeMetadataRequest.setIconType(iconTypeObj);
            } else if (currentTag === "94") {
              let iconBytes = this.fillIcon(str, currentParseOffsetNibbles);
              let lenOfIconBytes = ControlerUtil.getBERLengthInInt(
                str,
                currentParseOffsetNibbles + 2
              );
              let lenFieldSizeForIcon = ControlerUtil.getBERLengthSizeInNibbles(
                str,
                currentParseOffsetNibbles + 2
              );
              tagCompletelyConsumedNibbles =
                2 + lenFieldSizeForIcon + lenOfIconBytes * 2;
              storeMetadataRequest.setIcon(iconBytes);
            } else if (currentTag === "95") {
              let profileClassObj = this.fillProfileClass(
                str,
                currentParseOffsetNibbles
              );
              tagCompletelyConsumedNibbles = profileClassObj.getSize();
              storeMetadataRequest.setProfileClass(profileClassObj);
            } else if (currentTag === "99") {
              let pprIdsObj = this.fillPprIds(str, currentParseOffsetNibbles);
              tagCompletelyConsumedNibbles = pprIdsObj.getSize();
              storeMetadataRequest.setProfilePolicyRules(pprIdsObj);
            } else if (currentTag === "B6") {
              let notifConfigArray = this.fillNotificationConfigurationInfo(
                str,
                currentParseOffsetNibbles
              );
              storeMetadataRequest.setNotificationConfigurationInfo(
                notifConfigArray
              );
              let lenOfB6Bytes = ControlerUtil.getBERLengthInInt(
                str,
                currentParseOffsetNibbles + 2
              );
              let lenFieldSizeForB6 = ControlerUtil.getBERLengthSizeInNibbles(
                str,
                currentParseOffsetNibbles + 2
              );
              tagCompletelyConsumedNibbles =
                2 + lenFieldSizeForB6 + lenOfB6Bytes * 2;
            } else if (currentTag === "B7") {
              let operatorIdObj = this.fillOperatorId(
                str,
                currentParseOffsetNibbles
              );
              tagCompletelyConsumedNibbles = operatorIdObj.getSize();
              storeMetadataRequest.setProfileOwner(operatorIdObj);
            } else {
              console.warn(
                "[Frida PATCH v2] Unhandled Tag: " +
                  currentTag +
                  " at offset " +
                  currentParseOffsetNibbles +
                  ". Attempting to skip."
              );
              if (currentParseOffsetNibbles + 4 > str.length) {
                log(
                  "ERROR: [Frida PATCH v2] Not enough data to parse length of unhandled tag " +
                    currentTag +
                    ". Breaking."
                );
                break;
              }
              let lenOfUnhandledBytes = ControlerUtil.getBERLengthInInt(
                str,
                currentParseOffsetNibbles + 2
              );
              let lenFieldSizeUnhandled =
                ControlerUtil.getBERLengthSizeInNibbles(
                  str,
                  currentParseOffsetNibbles + 2
                );
              tagCompletelyConsumedNibbles =
                2 + lenFieldSizeUnhandled + lenOfUnhandledBytes * 2;
              log(
                "[Frida PATCH v2] Skipped " +
                  tagCompletelyConsumedNibbles +
                  " nibbles for unhandled tag " +
                  currentTag
              );
            }

            if (tagCompletelyConsumedNibbles > 0) {
              currentParseOffsetNibbles += tagCompletelyConsumedNibbles;
            } else {
              log(
                "ERROR: [Frida PATCH v2] Tag " +
                  currentTag +
                  " was not processed correctly (consumed 0 nibbles). Breaking loop to prevent infinite loop."
              );
              break;
            }
          } catch (e) {
            log(
              "ERROR: [Frida PATCH v2] Error processing tag " +
                currentTag +
                " at offset " +
                currentParseOffsetNibbles +
                ": " +
                e
            );
            log("ERROR: Stack: " + e.stack);
            try {
              if (currentParseOffsetNibbles + 4 <= str.length) {
                let lenOfUnhandledBytes = ControlerUtil.getBERLengthInInt(
                  str,
                  currentParseOffsetNibbles + 2
                );
                let lenFieldSizeUnhandled =
                  ControlerUtil.getBERLengthSizeInNibbles(
                    str,
                    currentParseOffsetNibbles + 2
                  );
                let skippedNibbles =
                  2 + lenFieldSizeUnhandled + lenOfUnhandledBytes * 2;
                console.warn(
                  "[Frida PATCH v2] Attempting to skip " +
                    skippedNibbles +
                    " nibbles after error on tag " +
                    currentTag
                );
                currentParseOffsetNibbles += skippedNibbles;
              } else {
                log(
                  "ERROR: [Frida PATCH v2] Not enough data to skip tag " +
                    currentTag +
                    " after error. Breaking."
                );
                break;
              }
            } catch (skipError) {
              log(
                "ERROR: [Frida PATCH v2] Error while trying to skip tag " +
                  currentTag +
                  " after initial error: " +
                  skipError +
                  ". Breaking."
              );
              break;
            }
          }
        }

        if (iterationCount >= MAX_ITERATIONS) {
          console.warn(
            "[Frida PATCH v2] Max iterations (" +
              MAX_ITERATIONS +
              ") reached for fillStoreMetadataRequest loop."
          );
        }
        const duration = now() - startTime;
        log(
          "[Frida PATCH v2] <---- FE.fillStoreMetadataRequest (PATCHED) exited. Duration: " +
            duration +
            "ms. Processed " +
            iterationCount +
            " tags."
        );
        return storeMetadataRequest;
      };
      log(
        "[Frida] Patched FillerEngine.fillStoreMetadataRequest() (v2) with correct Util and unhandled tag skipping."
      );
    } else {
      log(
        "ERROR: [Frida] Could not find FillerEngine.fillStoreMetadataRequest or ControlerUtil/StoreMetadataRequestClass to patch (v2)."
      );
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking FillerEngine: " + err);
  }

  try {
    const StoreMetaBIS = loadClass(
      "es.com.valid.lib_lpa.dataClasses.rspdefinitions.StoreMetadataRequestBIS"
    );
    const decodeStoreMetaBIS = StoreMetaBIS.decode.overload(
      "java.io.InputStream"
    );
    if (decodeStoreMetaBIS) {
      decodeStoreMetaBIS.implementation = function (is) {
        log("[Frida] ----> StoreMetaBIS.decode() entered");
        const startTime = now();
        try {
          decodeStoreMetaBIS.call(this, is);
        } catch (e) {
          log(
            "ERROR: [Frida] <---- StoreMetaBIS.decode() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <---- StoreMetaBIS.decode() exited. Duration: " +
            (now() - startTime) +
            "ms"
        );
      };
      log("[Frida] Hooked StoreMetadataRequestBIS.decode()");
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking StoreMetadataRequestBIS: " + err);
  }

  try {
    const SmdpSigned2 = loadClass(
      "es.com.valid.lib_lpa.dataClasses.rspdefinitions.SmdpSigned2"
    );
    const decodeSmdpSigned2 = SmdpSigned2.decode.overload(
      "java.io.InputStream"
    );
    if (decodeSmdpSigned2) {
      decodeSmdpSigned2.implementation = function (is) {
        log("[Frida] ----> SmdpSigned2.decode() entered");
        const startTime = now();
        try {
          decodeSmdpSigned2.call(this, is);
        } catch (e) {
          log(
            "ERROR: [Frida] <---- SmdpSigned2.decode() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <---- SmdpSigned2.decode() exited. Duration: " +
            (now() - startTime) +
            "ms"
        );
      };
      log("[Frida] Hooked SmdpSigned2.decode()");
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking SmdpSigned2: " + err);
  }

  // --- Section 2: DownloadControler - Other Key Methods ---
  log("\n[Frida] Setting up hooks for other DownloadControler methods...");
  try {
    const DC = loadClass("es.com.valid.lib_lpa.controler.DownloadControler");

    const downloadAndInstall =
      DC.downloadAndInstall.overload("java.lang.String");
    if (downloadAndInstall) {
      downloadAndInstall.implementation = function (s) {
        log("[Frida] ==> DC.downloadAndInstall(" + s + ") entered");
        const startTime = now();
        let result;
        try {
          result = downloadAndInstall.call(this, s);
        } catch (e) {
          log(
            "ERROR: [Frida] <== DC.downloadAndInstall() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <== DC.downloadAndInstall() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            result
        );
        return result;
      };
      log("[Frida] Hooked DC.downloadAndInstall()");
    }

    const getBoundProfilePkg = DC.getBoundProfilePackage.overload(
      "es.com.valid.lib_lpa.dataClasses.GetBoundProfilePackageRequest"
    );
    if (getBoundProfilePkg) {
      getBoundProfilePkg.implementation = function (req) {
        log("[Frida] ==> DC.getBoundProfilePackage(" + req + ") entered");
        const startTime = now();
        let result;
        try {
          result = getBoundProfilePkg.call(this, req);
        } catch (e) {
          log(
            "ERROR: [Frida] <== DC.getBoundProfilePackage() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <== DC.getBoundProfilePackage() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            result
        );
        return result;
      };
      log("[Frida] Hooked DC.getBoundProfilePackage()");
    }
    const acceptDownload = DC.acceptDownload.overload("java.lang.String");
    if (acceptDownload) {
      acceptDownload.implementation = function (s) {
        log("[Frida] ==> DC.acceptDownload(" + s + ") called");
        return acceptDownload.call(this, s);
      };
      log("[Frida] Hooked DC.acceptDownload()");
    }
    const checkPPRMethod = DC.checkPPR.overload(
      "es.com.valid.lib_lpa.dataClasses.StoreMetadataRequest"
    );
    if (checkPPRMethod) {
      checkPPRMethod.implementation = function (storeMetaReq) {
        // Use logArgs for consistent argument logging if complex, otherwise direct toString
        const argsString = storeMetaReq ? storeMetaReq.toString() : "null";
        log("[Frida] ==> DC.checkPPR(" + argsString + ") entered");
        const startTime = now();
        let result;
        try {
          result = checkPPRMethod.call(this, storeMetaReq);
        } catch (e) {
          log(
            "ERROR: [Frida] <== DC.checkPPR() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        // Use result.toString() for consistent result logging
        const resultString = result ? result.toString() : "null";
        log(
          "[Frida] <== DC.checkPPR() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            resultString
        );
        return result;
      };
      log("[Frida] Hooked DC.checkPPR(StoreMetadataRequest)");
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking other DC methods: " + err);
  }

  // --- Section 3: DownloadControler Listeners (via setDownloadControlerListener) ---
  log("\n[Frida] Setting up hooks for DownloadControlerListener...");
  try {
    const DC = loadClass("es.com.valid.lib_lpa.controler.DownloadControler");
    const setDCLis = DC.setDownloadControlerListener.overload(
      "es.com.valid.lib_lpa.controler.DownloadControler$DownloadControlerListener"
    );
    if (setDCLis) {
      setDCLis.implementation = function (listener) {
        log(
          "[Frida] ==> DC.setDownloadControlerListener(" + listener + ") called"
        );
        if (listener) {
          log(
            "[Frida]     Attempting to hook methods on provided listener instance: " +
              listener.$className
          );
          // Use Java.cast to get a wrapper for the specific listener instance's class
          const ListenerWrapper = Java.cast(
            listener,
            loadClass(listener.$className)
          );

          ListenerWrapper.onError.overload("java.lang.String").implementation =
            function (s) {
              log(
                "ERROR: [Frida] ### LISTENER ### " +
                  this.$className +
                  ".onError(" +
                  s +
                  ") called"
              );
              return this.onError(s); // Call original
            };
          ListenerWrapper.onFinished.overload(
            "java.lang.String"
          ).implementation = function (s) {
            log(
              "[Frida] ### LISTENER ### " +
                this.$className +
                ".onFinished(" +
                s +
                ") called"
            );
            return this.onFinished(s); // Call original
          };
          ListenerWrapper.onMutualAuthenticationCompleted.overload(
            "es.com.valid.lib_lpa.dataClasses.StoreMetadataRequest",
            "es.com.valid.lib_lpa.dataClasses.PprResult"
          ).implementation = function (storeMeta, pprResult) {
            log(
              "[Frida] ### LISTENER ### " +
                this.$className +
                ".onMutualAuthenticationCompleted(StoreMeta: " +
                storeMeta +
                ", PprResult: " +
                pprResult +
                ") called"
            );
            if (storeMeta && storeMeta.getIccid)
              log(
                "[Frida]       StoreMetadataRequest ICCID: " +
                  storeMeta.getIccid().getValueRotated()
              );
            return this.onMutualAuthenticationCompleted(storeMeta, pprResult); // Call original
          };
          ListenerWrapper.onProgress.overload("int").implementation = function (
            i
          ) {
            log(
              "[Frida] ### LISTENER ### " +
                this.$className +
                ".onProgress(" +
                i +
                ") called"
            );
            return this.onProgress(i); // Call original
          };
          log(
            "[Frida]     Successfully dynamically hooked methods on listener instance " +
              listener.$className
          );
        }
        return setDCLis.call(this, listener);
      };
      log("[Frida] Hooked DC.setDownloadControlerListener()");
    }
  } catch (err) {
    log(
      "ERROR: [Frida] Error hooking DC.setDownloadControlerListener or its callbacks: " +
        err
    );
  }

  // --- Section 4: CommunicationManager ---
  log("\n[Frida] Setting up hooks for CommunicationManager...");
  try {
    const CM = loadClass(
      "es.com.valid.lib_lpa.cardCommunication.CommunicationManager"
    );
    const methodsToHookCM = [
      { name: "openConnection", overloads: [[]] },
      { name: "closeConnection", overloads: [[]] },
      { name: "getEuiccInfo1", overloads: [[]] },
      { name: "getEuiccChallenge", overloads: [[]] },
      {
        name: "AuthenticateServer",
        overloads: [
          [
            "es.com.valid.lib_lpa.dataClasses.InitiateAuthenticationResponse",
            "es.com.valid.lib_lpa.dataClasses.CtxParams1",
          ],
        ],
      },
      {
        name: "prepareDownload",
        overloads: [
          ["es.com.valid.lib_lpa.dataClasses.PrepareDownloadRequest"],
        ],
      },
      { name: "executeScriptAndGetData", overloads: [["[Ljava.lang.String;"]] },
    ];
    methodsToHookCM.forEach((m) => {
      m.overloads.forEach((ovArgs) => {
        try {
          const method = CM[m.name].overload(...ovArgs);
          method.implementation = function () {
            const argsString = logArgs(arguments);
            log("[Frida] ==> CM." + m.name + "(" + argsString + ") entered");
            const startTime = now();
            let result;
            try {
              result = method.apply(this, arguments);
            } catch (e) {
              log(
                "ERROR: [Frida] <== CM." +
                  m.name +
                  "() EXCEPTION: " +
                  e +
                  " Duration: " +
                  (now() - startTime) +
                  "ms"
              );
              throw e;
            }
            let resultStr = result ? result.toString() : "null";
            if (
              result &&
              resultStr.startsWith("[B@") &&
              result.length &&
              typeof result[0] === "number"
            )
              resultStr = bytesToHex(result);
            log(
              "[Frida] <== CM." +
                m.name +
                "() exited. Duration: " +
                (now() - startTime) +
                "ms. Result: " +
                resultStr
            );
            return result;
          };
        } catch (e) {
          console.warn(
            "[Frida] Failed to hook CM." +
              m.name +
              " with overload " +
              ovArgs.join(",") +
              ": " +
              e
          );
        }
      });
    });
    log("[Frida] Hooked CommunicationManager methods");
  } catch (err) {
    log("ERROR: [Frida] Error hooking CommunicationManager: " + err);
  }

  // --- Section 5: ServerCommunicationManager ---
  log("\n[Frida] Setting up hooks for ServerCommunicationManager...");
  try {
    const SCM = loadClass(
      "es.com.valid.lib_lpa.serverCommunication.ServerCommunicationManager"
    );
    const makeRequest = SCM.makeRequest.overload(
      "java.lang.String",
      "java.lang.String"
    );
    if (makeRequest) {
      makeRequest.implementation = function (url, jsonPayload) {
        log(
          "[Frida] ==> SCM.makeRequest(URL: " +
            url +
            ", Payload: " +
            jsonPayload.substring(0, 200) +
            "...) entered"
        );
        const startTime = now();
        let result;
        try {
          result = makeRequest.call(this, url, jsonPayload);
        } catch (e) {
          log(
            "ERROR: [Frida] <== SCM.makeRequest() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        if (result) {
          log(
            "[Frida] <== SCM.makeRequest() exited. Duration: " +
              (now() - startTime) +
              "ms. Result[0](status): " +
              result[0] +
              ", Result[1](body): " +
              (result[1] ? result[1].substring(0, 200) + "..." : "null")
          );
        } else {
          log(
            "[Frida] <== SCM.makeRequest() exited. Duration: " +
              (now() - startTime) +
              "ms. Result: null"
          );
        }
        return result;
      };
      log("[Frida] Hooked SCM.makeRequest()");
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking ServerCommunicationManager: " + err);
  }

  // --- Section 6: AsyncTasks ---
  log("\n[Frida] Setting up hooks for AsyncTasks...");
  try {
    const MutualAuthTask = loadClass(
      "es.com.valid.lib_lpa.controler.DownloadControler$MutualAtutheticationTask"
    );
    const doInBgMutual =
      MutualAuthTask.doInBackground.overload("[Ljava.lang.Void;");
    if (doInBgMutual) {
      doInBgMutual.implementation = function (args) {
        log("[Frida] ==> MutualAuthTask.doInBackground() entered");
        const startTime = now();
        let result;
        try {
          result = doInBgMutual.call(this, args);
        } catch (e) {
          log(
            "ERROR: [Frida] <== MutualAuthTask.doInBackground() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <== MutualAuthTask.doInBackground() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            result
        );
        return result;
      };
      log("[Frida] Hooked MutualAuthTask.doInBackground()");
    }
    const onPostExecMutual = MutualAuthTask.onPostExecute.overload(
      "es.com.valid.lib_lpa.dataClasses.StoreMetadataRequest"
    );
    if (onPostExecMutual) {
      onPostExecMutual.implementation = function (storeMetaReq) {
        log(
          "[Frida] ==> MutualAuthTask.onPostExecute(" +
            storeMetaReq +
            ") entered"
        );
        return onPostExecMutual.call(this, storeMetaReq);
      };
      log("[Frida] Hooked MutualAuthTask.onPostExecute()");
    }
  } catch (err) {
    console.warn(
      "[Frida] Could not hook DownloadControler$MutualAtutheticationTask: " +
        err
    );
  }

  try {
    const DownloadTask = loadClass(
      "es.com.valid.lib_lpa.controler.DownloadControler$DownloadAndInstallationTask"
    );
    const doInBgDownload = DownloadTask.doInBackground.overload(
      "[Ljava.lang.String;"
    );
    if (doInBgDownload) {
      doInBgDownload.implementation = function (args) {
        log(
          "[Frida] ==> DownloadTask.doInBackground(" +
            (args && args[0]) +
            ") entered"
        );
        const startTime = now();
        let result;
        try {
          result = doInBgDownload.call(this, args);
        } catch (e) {
          log(
            "ERROR: [Frida] <== DownloadTask.doInBackground() EXCEPTION: " +
              e +
              " Duration: " +
              (now() - startTime) +
              "ms"
          );
          throw e;
        }
        log(
          "[Frida] <== DownloadTask.doInBackground() exited. Duration: " +
            (now() - startTime) +
            "ms. Result: " +
            result
        );
        return result;
      };
      log("[Frida] Hooked DownloadTask.doInBackground()");
    }
    const onPostExecDownload =
      DownloadTask.onPostExecute.overload("java.lang.Void");
    if (onPostExecDownload) {
      onPostExecDownload.implementation = function (v) {
        log("[Frida] ==> DownloadTask.onPostExecute(Void) entered");
        return onPostExecDownload.call(this, v);
      };
      log("[Frida] Hooked DownloadTask.onPostExecute()");
    }
  } catch (err) {
    console.warn(
      "[Frida] Could not hook DownloadControler$DownloadAndInstallationTask: " +
        err
    );
  }

  // --- Section 7: ProfileInfoControler ---
  log("\n[Frida] Setting up hooks for ProfileInfoControler...");
  try {
    const PIC = loadClass(
      "es.com.valid.lib_lpa.controler.ProfileInfoControler"
    );
    const EnableTask = loadClass(
      "es.com.valid.lib_lpa.controler.ProfileInfoControler$EnableTask"
    );
    // CommunicationManager (CM) should already be loaded by previous sections, but ensure it's available
    const CM_PIC = loadClass(
      "es.com.valid.lib_lpa.cardCommunication.CommunicationManager"
    );

    if (PIC) {
      const setPICLis = PIC.setProfileInfoControlerListener.overload(
        "es.com.valid.lib_lpa.controler.ProfileInfoControler$ProfileInfoControlerListener"
      );
      if (setPICLis) {
        setPICLis.implementation = function (listener) {
          log(
            "[Frida] ==> PIC.setProfileInfoControlerListener(" +
              listener +
              ") called"
          );
          if (listener) {
            log(
              "[Frida]     Attempting to hook methods on PIC listener instance: " +
                listener.$className
            );
            const ListenerWrapper = Java.cast(
              listener,
              loadClass(listener.$className)
            );

            ListenerWrapper.onError.overload(
              "java.lang.String"
            ).implementation = function (s) {
              log(
                "ERROR: [Frida] ### PIC LISTENER ### " +
                  this.$className +
                  ".onError(" +
                  s +
                  ") called"
              );
              if (s && s.toString().includes("disallowedByPolicy")) {
                log(
                  "ERROR: [Frida] ### PIC LISTENER ### onError 'disallowedByPolicy' stack trace: " +
                    Java.use("android.util.Log").getStackTraceString(
                      Java.use("java.lang.Throwable").$new()
                    )
                );
              }
              return this.onError(s); // Call original
            };
            ListenerWrapper.onEnable.overload(
              "java.lang.String"
            ).implementation = function (s) {
              log(
                "[Frida] ### PIC LISTENER ### " +
                  this.$className +
                  ".onEnable(" +
                  s +
                  ") called"
              );
              return this.onEnable(s);
            };
            ListenerWrapper.onDisable.overload(
              "java.lang.String"
            ).implementation = function (s) {
              log(
                "[Frida] ### PIC LISTENER ### " +
                  this.$className +
                  ".onDisable(" +
                  s +
                  ") called"
              );
              return this.onDisable(s);
            };
            ListenerWrapper.onDelete.overload(
              "java.lang.String"
            ).implementation = function (s) {
              log(
                "[Frida] ### PIC LISTENER ### " +
                  this.$className +
                  ".onDelete(" +
                  s +
                  ") called"
              );
              return this.onDelete(s);
            };
            ListenerWrapper.onsetNickName.overload(
              "java.lang.String"
            ).implementation = function (s) {
              log(
                "[Frida] ### PIC LISTENER ### " +
                  this.$className +
                  ".onsetNickName(" +
                  s +
                  ") called"
              );
              return this.onsetNickName(s);
            };
            log(
              "[Frida]     Successfully dynamically hooked methods on PIC listener instance " +
                listener.$className
            );
          }
          return setPICLis.call(this, listener);
        };
        log("[Frida] Hooked PIC.setProfileInfoControlerListener()");
      }
    }

    if (EnableTask) {
      const onPreExecuteEnable = EnableTask.onPreExecute.overload();
      if (onPreExecuteEnable) {
        onPreExecuteEnable.implementation = function () {
          log("[Frida] ==> PIC$EnableTask.onPreExecute() entered");
          const startTime = now();
          try {
            onPreExecuteEnable.call(this);
          } catch (e) {
            log(
              "ERROR: [Frida] <== PIC$EnableTask.onPreExecute() EXCEPTION: " +
                e +
                " Duration: " +
                (now() - startTime) +
                "ms"
            );
            throw e;
          }
          log(
            "[Frida] <== PIC$EnableTask.onPreExecute() exited. Duration: " +
              (now() - startTime) +
              "ms"
          );
        };
        log("[Frida] Hooked PIC$EnableTask.onPreExecute()");
      }

      const doInBackgroundEnable =
        EnableTask.doInBackground.overload("[Ljava.lang.Void;");
      if (doInBackgroundEnable) {
        doInBackgroundEnable.implementation = function (args) {
          log(
            "[Frida] ==> PIC$EnableTask.doInBackground(" +
              logArgs(args) +
              ") entered"
          );
          const startTime = now();
          let result;
          try {
            result = doInBackgroundEnable.call(this, args);
          } catch (e) {
            log(
              "ERROR: [Frida] <== PIC$EnableTask.doInBackground() EXCEPTION: " +
                e +
                " Duration: " +
                (now() - startTime) +
                "ms"
            );
            throw e;
          }
          log(
            "[Frida] <== PIC$EnableTask.doInBackground() exited. Duration: " +
              (now() - startTime) +
              "ms. Result: " +
              result
          );
          return result;
        };
        log("[Frida] Hooked PIC$EnableTask.doInBackground()");
      }

      const onPostExecuteEnable =
        EnableTask.onPostExecute.overload("java.lang.Void");
      if (onPostExecuteEnable) {
        onPostExecuteEnable.implementation = function (v) {
          log(
            "[Frida] ==> PIC$EnableTask.onPostExecute(Void: " + v + ") entered"
          );
          try {
            // Access the 'result' field of the EnableTask instance
            const taskInstance = Java.cast(this, EnableTask);
            const resultField = taskInstance.class.getDeclaredField("result");
            resultField.setAccessible(true);
            const taskResult = resultField.get(taskInstance);
            log("[Frida]       PIC$EnableTask.result field: " + taskResult);
            if (
              taskResult &&
              taskResult.toString().includes("disallowedByPolicy")
            ) {
              log(
                "ERROR: [Frida]       PIC$EnableTask.result is 'disallowedByPolicy'. Stack: " +
                  Java.use("android.util.Log").getStackTraceString(
                    Java.use("java.lang.Throwable").$new()
                  )
              );
            }
          } catch (fieldError) {
            console.warn(
              "[Frida]       Could not access PIC$EnableTask.result field: " +
                fieldError
            );
          }
          const startTime = now();
          try {
            onPostExecuteEnable.call(this, v);
          } catch (e) {
            log(
              "ERROR: [Frida] <== PIC$EnableTask.onPostExecute() EXCEPTION: " +
                e +
                " Duration: " +
                (now() - startTime) +
                "ms"
            );
            throw e;
          }
          log(
            "[Frida] <== PIC$EnableTask.onPostExecute() exited. Duration: " +
              (now() - startTime) +
              "ms"
          );
        };
        log("[Frida] Hooked PIC$EnableTask.onPostExecute()");
      }
    }

    if (CM_PIC) {
      // CommunicationManager
      const enableProfileCM = CM_PIC.enableProfile.overload(
        "es.com.valid.lib_lpa.dataClasses.ProfileInfo"
      );
      if (enableProfileCM) {
        enableProfileCM.implementation = function (profileInfo) {
          log(
            "[Frida] ==> CM.enableProfile(ProfileInfo: " +
              profileInfo +
              ") entered"
          );
          if (profileInfo) {
            try {
              log(
                "[Frida]       ProfileInfo ICCID: " +
                  profileInfo.getIccid().getValueRotated()
              );
              log(
                "[Frida]       ProfileInfo State: " +
                  profileInfo.getProfileState().getValueString()
              );
              const nicknameObj = profileInfo.getProfileNickname();
              log(
                "[Frida]       ProfileInfo Nickname: " +
                  (nicknameObj ? nicknameObj.getValueString() : "null")
              );
              log(
                "[Frida]       ProfileInfo Class: " +
                  profileInfo.getProfileClass().getValueString()
              );
            } catch (piError) {
              console.warn(
                "[Frida]       Error accessing ProfileInfo fields: " + piError
              );
            }
          }
          const startTime = now();
          let result;
          try {
            result = enableProfileCM.call(this, profileInfo);
          } catch (e) {
            log(
              "ERROR: [Frida] <== CM.enableProfile() EXCEPTION: " +
                e +
                " Duration: " +
                (now() - startTime) +
                "ms"
            );
            throw e;
          }
          // Result from CM.enableProfile is already a hex string
          log(
            "[Frida] <== CM.enableProfile() exited. Duration: " +
              (now() - startTime) +
              "ms. Result (hex): " +
              result // Corrected: result is already a hex string
          );
          if (result) {
            log(
              "[Frida]       Entering 'if (result)' block. Result is: " + result
            );
            try {
              log("[Frida]       Attempting to load LocalUtilClass...");
              const LocalUtilClass = loadClass(
                "es.com.valid.lib_lpa.common.Util"
              );
              log(
                "[Frida]       LocalUtilClass loaded: " +
                  (LocalUtilClass ? LocalUtilClass.$className : "null")
              );

              if (!LocalUtilClass) {
                log(
                  "ERROR: [Frida]       FATAL: es.com.valid.lib_lpa.common.Util class not loaded. Cannot decode CM.enableProfile result."
                );
              } else {
                log(
                  "[Frida]       Attempting LocalUtilClass.HexToArray(result)..."
                );
                let javaByteArray = LocalUtilClass.HexToArray(result);
                log(
                  "[Frida]       HexToArray result (byte array): " +
                    javaByteArray
                );
                log("[Frida]       Attempting new ByteArrayInputStream...");
                let byteArrayInputStream = Java.use(
                  "java.io.ByteArrayInputStream"
                ).$new(javaByteArray);
                log(
                  "[Frida]       ByteArrayInputStream created: " +
                    byteArrayInputStream
                );
                log("[Frida]       Attempting new EnableProfileResponse...");
                let enableProfileResponse = Java.use(
                  "es.com.valid.lib_lpa.dataClasses.rspdefinitions.EnableProfileResponse"
                ).$new();
                log(
                  "[Frida]       EnableProfileResponse created: " +
                    enableProfileResponse
                );
                log(
                  "[Frida]       Attempting enableProfileResponse.decode()..."
                );
                enableProfileResponse.decode(byteArrayInputStream);
                log("[Frida]       enableProfileResponse.decode() completed.");
                let enableResultValue = enableProfileResponse
                  .getEnableResult()
                  .value.intValue();
                log("[Frida]       enableResultValue: " + enableResultValue);
                let enableResultString = LocalUtilClass.getEnableProfileResult(
                  Java.use("java.lang.Integer").valueOf(enableResultValue)
                );
                log("[Frida]       enableResultString: " + enableResultString);
                log(
                  "[Frida]       Decoded CM.enableProfile result: Status Code: " +
                    enableResultValue +
                    ", Meaning: '" +
                    enableResultString +
                    "'"
                );
                if (
                  enableResultString &&
                  enableResultString.toString().includes("disallowedByPolicy")
                ) {
                  log(
                    "ERROR: [Frida]       CM.enableProfile result is 'disallowedByPolicy'. Stack: " +
                      Java.use("android.util.Log").getStackTraceString(
                        Java.use("java.lang.Throwable").$new()
                      )
                  );
                }
              }
            } catch (decodeError) {
              console.warn(
                "[Frida]       Failed to decode CM.enableProfile result as EnableProfileResponse: " +
                  decodeError
              );
              console.warn(
                "[Frida]       Decode Error Stack: " + decodeError.stack
              );
            }
            log("[Frida]       Exiting 'if (result)' block.");
          } else {
            log(
              "[Frida]       Skipped 'if (result)' block because result was falsy."
            );
          }
          return result;
        };
        log("[Frida] Hooked CM.enableProfile(ProfileInfo)");
      }
    }
  } catch (err) {
    log("ERROR: [Frida] Error hooking ProfileInfoControler or related: " + err);
  }

  try {
    const InetAddress = Java.use("java.net.InetAddress");

    const getDns = (hostname) => {
      log(`[Frida] Called getDns ${hostname}`);
      const dns = penumbraClient.dns.value;
      log(`[Frida] Got dns object ${dns}`);
      return dns.lookup.overload("java.lang.String").call(dns, hostname);
    };

    const getByName = InetAddress.getByName.overload("java.lang.String");
    getByName.implementation = function (hostname) {
      const resolvedHostname = getDns(hostname);
      log(`[Frida] Transforming ${hostname} into ${resolvedHostname}`);
      // Feed output back into getByName (which will resolve to itself)
      return getByName.call(this, resolvedHostname);
    };

    // Also hook getAllByName() for completeness
    const getAllByName = InetAddress.getAllByName.overload("java.lang.String");
    getAllByName.implementation = function (hostname) {
      const resolvedHostname = getDns(hostname);
      log(`[Frida] Transforming ${hostname} into ${resolvedHostname}`);
      // Feed output back into getByName (which will resolve to itself)
      return getAllByName.call(this, resolvedHostname);
    };

    log("[Frida] Hooked InetAddress DNS resolution methods");
  } catch (err) {
    log("[Frida] Error hooking InetAddress: " + err);
  }

  // SSL bypass
  // try {
  //   const TrustManagerImpl = Java.use(
  //     "com.android.org.conscrypt.TrustManagerImpl"
  //   );

  //   TrustManagerImpl.verifyChain.implementation = function (
  //     untrustedChain,
  //     trustAnchorChain,
  //     host,
  //     clientAuth,
  //     ocspData,
  //     tlsSctData
  //   ) {
  //     log("[Frida] Certificate verification bypassed for: " + host);
  //     return untrustedChain;
  //   };

  //   log("[Frida] Low-level certificate validation bypassed");
  // } catch (e) {
  //   log("[Frida] Could not hook TrustManagerImpl: " + e.message);
  // }

  // try {
  //   const ConscryptTrustManagerImpl = Java.use(
  //     "com.android.org.conscrypt.TrustManagerImpl"
  //   );

  //   ConscryptTrustManagerImpl.checkTrustedRecursive.implementation = function (
  //     certs,
  //     host,
  //     clientAuth,
  //     untrustedChain,
  //     trustAnchorChain,
  //     used
  //   ) {
  //     log("[Frida] Recursive trust check bypassed for: " + host);
  //     return Java.use("java.util.ArrayList").$new();
  //   };

  //   log("[Frida] Recursive trust validation bypassed");
  // } catch (e) {
  //   log("[Frida] Could not hook recursive trust check: " + e.message);
  // }

  // // Hook the specific checkTrusted overloads
  // try {
  //   const TrustManagerImpl = Java.use(
  //     "com.android.org.conscrypt.TrustManagerImpl"
  //   );

  //   // Hook first overload
  //   TrustManagerImpl.checkTrusted.overload(
  //     "[Ljava.security.cert.X509Certificate;",
  //     "java.lang.String",
  //     "javax.net.ssl.SSLSession",
  //     "javax.net.ssl.SSLParameters",
  //     "boolean"
  //   ).implementation = function (
  //     certs,
  //     authType,
  //     session,
  //     parameters,
  //     clientAuth
  //   ) {
  //     log("[Frida] checkTrusted overload 1 bypassed - authType: " + authType);
  //     return Java.use("java.util.ArrayList").$new();
  //   };

  //   // Hook second overload
  //   TrustManagerImpl.checkTrusted.overload(
  //     "[Ljava.security.cert.X509Certificate;",
  //     "[B",
  //     "[B",
  //     "java.lang.String",
  //     "java.lang.String",
  //     "boolean"
  //   ).implementation = function (
  //     certs,
  //     ocspData,
  //     tlsSctData,
  //     host,
  //     authType,
  //     clientAuth
  //   ) {
  //     log(
  //       "[Frida] checkTrusted overload 2 bypassed - host: " +
  //         host +
  //         ", authType: " +
  //         authType
  //     );
  //     return Java.use("java.util.ArrayList").$new();
  //   };

  //   log("[Frida] Both checkTrusted overloads bypassed");
  // } catch (e) {
  //   log("[Frida] Could not hook checkTrusted overloads: " + e.message);
  // }

  // // Hook certificate path validation
  // try {
  //   const CertPathValidator = Java.use("java.security.cert.CertPathValidator");

  //   CertPathValidator.validate.overload(
  //     "java.security.cert.CertPath",
  //     "java.security.cert.CertPathParameters"
  //   ).implementation = function (certPath, params) {
  //     log("[Frida] CertPathValidator.validate bypassed");

  //     // Return a successful validation result
  //     try {
  //       const PKIXCertPathValidatorResult = Java.use(
  //         "java.security.cert.PKIXCertPathValidatorResult"
  //       );
  //       const TrustAnchor = Java.use("java.security.cert.TrustAnchor");
  //       const X500Principal = Java.use(
  //         "javax.security.auth.x500.X500Principal"
  //       );

  //       // Create a dummy successful result
  //       const dummyPrincipal = X500Principal.$new("CN=Frida-Bypass");
  //       const dummyTrustAnchor = TrustAnchor.$new(dummyPrincipal, null);
  //       return PKIXCertPathValidatorResult.$new(
  //         dummyTrustAnchor,
  //         null,
  //         dummyPrincipal
  //       );
  //     } catch (resultError) {
  //       log("[Frida] Error creating validation result: " + resultError.message);
  //       // If we can't create a result, try the original and suppress exceptions
  //       try {
  //         return this.validate(certPath, params);
  //       } catch (validationError) {
  //         log("[Frida] Original validation failed, creating minimal result");
  //         throw Java.use("java.lang.RuntimeException").$new(
  //           "Validation bypassed by Frida"
  //         );
  //       }
  //     }
  //   };

  //   log("[Frida] CertPathValidator hooked");
  // } catch (e) {
  //   log("[Frida] Could not hook CertPathValidator: " + e.message);
  // }

  // // Hook the PKIXCertPathValidator specifically
  // try {
  //   const PKIXCertPathValidator = Java.use(
  //     "sun.security.provider.certpath.PKIXCertPathValidator"
  //   );

  //   PKIXCertPathValidator.validate.implementation = function (
  //     params,
  //     certPath
  //   ) {
  //     log("[Frida] PKIXCertPathValidator.validate bypassed");

  //     const PKIXCertPathValidatorResult = Java.use(
  //       "java.security.cert.PKIXCertPathValidatorResult"
  //     );
  //     const TrustAnchor = Java.use("java.security.cert.TrustAnchor");
  //     const X500Principal = Java.use("javax.security.auth.x500.X500Principal");

  //     try {
  //       const dummyPrincipal = X500Principal.$new("CN=Frida-PKIX-Bypass");
  //       const dummyTrustAnchor = TrustAnchor.$new(dummyPrincipal, null);
  //       return PKIXCertPathValidatorResult.$new(
  //         dummyTrustAnchor,
  //         null,
  //         dummyPrincipal
  //       );
  //     } catch (e) {
  //       log("[Frida] Error in PKIX bypass: " + e.message);
  //       throw e;
  //     }
  //   };

  //   log("[Frida] PKIXCertPathValidator hooked");
  // } catch (e) {
  //   log("[Frida] Could not hook PKIXCertPathValidator: " + e.message);
  // }

  // // Hook TrustAnchor lookups
  // try {
  //   const TrustAnchorManager = Java.use(
  //     "com.android.org.conscrypt.TrustedCertificateStore"
  //   );

  //   if (TrustAnchorManager.findIssuer) {
  //     TrustAnchorManager.findIssuer.implementation = function (cert) {
  //       log("[Frida] TrustedCertificateStore.findIssuer bypassed");
  //       return cert; // Return the certificate itself as a trusted issuer
  //     };
  //   }

  //   log("[Frida] TrustedCertificateStore hooked");
  // } catch (e) {
  //   log("[Frida] Could not hook TrustedCertificateStore: " + e.message);
  // }

  // // Add debugging for the specific error
  // try {
  //   const CertPathValidatorException = Java.use(
  //     "java.security.cert.CertPathValidatorException"
  //   );

  //   CertPathValidatorException.$init.overload(
  //     "java.lang.String"
  //   ).implementation = function (message) {
  //     if (
  //       message &&
  //       message.includes("Trust anchor for certification path not found")
  //     ) {
  //       log(
  //         "[Frida] *** BLOCKING CertPathValidatorException: " + message + " ***"
  //       );

  //       // Print stack trace to see where this is coming from
  //       const Thread = Java.use("java.lang.Thread");
  //       const stackTrace = Thread.currentThread().getStackTrace();
  //       for (let i = 0; i < Math.min(stackTrace.length, 8); i++) {
  //         log("[Frida] Stack " + i + ": " + stackTrace[i].toString());
  //       }

  //       // Instead of creating the exception, throw a different one or return
  //       throw Java.use("java.lang.RuntimeException").$new(
  //         "Certificate validation bypassed by Frida"
  //       );
  //     }
  //     return this.$init(message);
  //   };

  //   log("[Frida] CertPathValidatorException constructor hooked");
  // } catch (e) {
  //   log("[Frida] Could not hook CertPathValidatorException: " + e.message);
  // }

  // --- Section 8: CLI Command Completion Callbacks ---
  log(
    "\n[Frida] Setting up hooks for CLI command completion detection using existing callback interfaces..."
  );

  // Global callback handler - the main Java program can set this
  let javaCallbackHandler = null;

  // Function to register a Java callback handler from the main program
  global.setJavaCallbackHandler = function (handler) {
    javaCallbackHandler = handler;
    log("[Frida] Java callback handler registered");
  };

  // Function to notify Java code about callback completion via direct method call
  function notifyJavaCallback(operationType, operationName, result, isError) {
    try {
      log(
        `[Frida] CLI operation completed: ${operationType}.${operationName}, result: ${result}, isError: ${isError}`
      );

      // Try to access the MockFactoryService static instance
      Java.scheduleOnMainThread(function () {
        try {
          const MockFactoryService = loadClass(
            "com.penumbraos.esim.MockFactoryService"
          );
          const instance = MockFactoryService.fridaCallbackInstance.value;

          if (instance) {
            instance.onFridaCallback(
              operationType,
              operationName,
              result || "",
              isError
            );
            log(
              `[Frida] Successfully called Java callback handler via static reference`
            );
          } else {
            log(
              "[Frida] MockFactoryService instance is null, cannot call callback"
            );
          }
        } catch (e) {
          log(`[Frida] Error calling Java callback via static reference: ${e}`);

          // Fallback: try the original handler if registered
          if (javaCallbackHandler) {
            try {
              javaCallbackHandler.onFridaCallback(
                operationType,
                operationName,
                result || "",
                isError
              );
              log(`[Frida] Successfully called fallback Java callback handler`);
            } catch (e2) {
              log(
                `[Frida] Error calling fallback Java callback handler: ${e2}`
              );
            }
          } else {
            log(
              "[Frida] No callback handlers available, operation completed but not handled"
            );
          }
        }
      });
    } catch (e) {
      log(`[Frida] Error in notifyJavaCallback: ${e}`);
    }
  }

  // Expose method for Java to call to register callback handler
  global.registerFridaCallback = function (callbackObject) {
    javaCallbackHandler = callbackObject;
    log("[Frida] Callback handler registered from Java side");
  };

  try {
    // Hook factoryService.setSysProp to catch operations that don't use listeners
    const factoryService = loadClass(
      "humane.connectivity.esimlpa.factoryService"
    );
    if (factoryService) {
      const setSysProp = factoryService.setSysProp.overload(
        "java.lang.String",
        "java.lang.String"
      );
      if (setSysProp) {
        setSysProp.implementation = function (key, value) {
          log(`[Frida] setSysProp called: ${key} = ${value}`);

          // Call original method first
          const result = setSysProp.call(this, key, value);

          // Check if this indicates command completion
          if (key === "humane.esim.lastintent.result") {
            log(
              `[Frida] ### CLI COMMAND COMPLETED via setSysProp ### Result: ${value}`
            );

            // Determine operation type based on result value
            const isError =
              value &&
              (value.includes("Error") ||
                value.includes("No ") ||
                value.includes("Couldn't"));
            const operationType = "factoryService";
            const operationName = value.includes("getProfile")
              ? "getProfiles"
              : value.includes("Get EID")
              ? "getEid"
              : value.includes("Get Ative profile")
              ? "getActiveProfile"
              : value.includes("ICCID")
              ? "getActiveProfileIccid"
              : "setSysProp";

            notifyJavaCallback(operationType, operationName, value, isError);
          }

          return result;
        };
        log(
          "[Frida] Hooked factoryService.setSysProp() for CLI completion detection"
        );
      }
    }

    // Hook ProfileInfoControler listener methods
    const ProfileInfoControlerListener = loadClass(
      "es.com.valid.lib_lpa.controler.ProfileInfoControler$ProfileInfoControlerListener"
    );
    if (ProfileInfoControlerListener) {
      // Hook onEnable completion
      if (ProfileInfoControlerListener.onEnable) {
        ProfileInfoControlerListener.onEnable.implementation = function (
          result
        ) {
          log(`[Frida] ### PROFILE ENABLE COMPLETED ### Result: ${result}`);
          const returnValue = this.onEnable(result);
          notifyJavaCallback("ProfileInfoControler", "onEnable", result, false);
          return returnValue;
        };
      }

      // Hook onDisable completion
      if (ProfileInfoControlerListener.onDisable) {
        ProfileInfoControlerListener.onDisable.implementation = function (
          result
        ) {
          log(`[Frida] ### PROFILE DISABLE COMPLETED ### Result: ${result}`);
          const returnValue = this.onDisable(result);
          notifyJavaCallback(
            "ProfileInfoControler",
            "onDisable",
            result,
            false
          );
          return returnValue;
        };
      }

      // Hook onDelete completion
      if (ProfileInfoControlerListener.onDelete) {
        ProfileInfoControlerListener.onDelete.implementation = function (
          result
        ) {
          log(`[Frida] ### PROFILE DELETE COMPLETED ### Result: ${result}`);
          const returnValue = this.onDelete(result);
          notifyJavaCallback("ProfileInfoControler", "onDelete", result, false);
          return returnValue;
        };
      }

      // Hook onsetNickName completion
      if (ProfileInfoControlerListener.onsetNickName) {
        ProfileInfoControlerListener.onsetNickName.implementation = function (
          result
        ) {
          log(
            `[Frida] ### PROFILE SET NICKNAME COMPLETED ### Result: ${result}`
          );
          const returnValue = this.onsetNickName(result);
          notifyJavaCallback(
            "ProfileInfoControler",
            "onsetNickName",
            result,
            false
          );
          return returnValue;
        };
      }

      // Hook onError for profile operations
      if (ProfileInfoControlerListener.onError) {
        ProfileInfoControlerListener.onError.implementation = function (
          result
        ) {
          log(`[Frida] ### PROFILE OPERATION ERROR ### Result: ${result}`);
          const returnValue = this.onError(result);
          notifyJavaCallback("ProfileInfoControler", "onError", result, true);
          return returnValue;
        };
      }

      log("[Frida] Hooked ProfileInfoControlerListener methods");
    }

    // Hook DownloadControler listener methods
    const DownloadControlerListener = loadClass(
      "es.com.valid.lib_lpa.controler.DownloadControler$DownloadControlerListener"
    );
    if (DownloadControlerListener) {
      // Hook onFinished completion
      if (DownloadControlerListener.onFinished) {
        DownloadControlerListener.onFinished.implementation = function (
          result
        ) {
          log(`[Frida] ### DOWNLOAD FINISHED ### Result: ${result}`);
          const returnValue = this.onFinished(result);
          notifyJavaCallback("DownloadControler", "onFinished", result, false);
          return returnValue;
        };
      }

      // Hook onError for download operations
      if (DownloadControlerListener.onError) {
        DownloadControlerListener.onError.implementation = function (result) {
          log(`[Frida] ### DOWNLOAD ERROR ### Result: ${result}`);
          const returnValue = this.onError(result);
          notifyJavaCallback("DownloadControler", "onError", result, true);
          return returnValue;
        };
      }

      log("[Frida] Hooked DownloadControlerListener methods");
    }

    // Hook EuiccLevelController listener methods
    const EuiccLevelControllerListener = loadClass(
      "es.com.valid.lib_lpa.controler.EuiccLevelController$EuiccLevelControllerListener"
    );
    if (EuiccLevelControllerListener) {
      // Hook onGetEid completion
      if (EuiccLevelControllerListener.onGetEid) {
        EuiccLevelControllerListener.onGetEid.implementation = function (
          result
        ) {
          log(`[Frida] ### GET EID COMPLETED ### Result: ${result}`);
          const returnValue = this.onGetEid(result);
          notifyJavaCallback("EuiccLevelController", "onGetEid", result, false);
          return returnValue;
        };
      }

      // Hook onMemoryReset completion
      if (EuiccLevelControllerListener.onMemoryReset) {
        EuiccLevelControllerListener.onMemoryReset.implementation = function (
          result
        ) {
          log(`[Frida] ### MEMORY RESET COMPLETED ### Result: ${result}`);
          const returnValue = this.onMemoryReset(result);
          notifyJavaCallback(
            "EuiccLevelController",
            "onMemoryReset",
            result,
            false
          );
          return returnValue;
        };
      }

      // Hook onTestMemoryReset completion
      if (EuiccLevelControllerListener.onTestMemoryReset) {
        EuiccLevelControllerListener.onTestMemoryReset.implementation =
          function (result) {
            log(
              `[Frida] ### TEST MEMORY RESET COMPLETED ### Result: ${result}`
            );
            const returnValue = this.onTestMemoryReset(result);
            notifyJavaCallback(
              "EuiccLevelController",
              "onTestMemoryReset",
              result,
              false
            );
            return returnValue;
          };
      }

      // Hook onSetDefaultSMDPPlus completion
      if (EuiccLevelControllerListener.onSetDefaultSMDPPlus) {
        EuiccLevelControllerListener.onSetDefaultSMDPPlus.implementation =
          function (result) {
            log(
              `[Frida] ### SET DEFAULT SMDP PLUS COMPLETED ### Result: ${result}`
            );
            const returnValue = this.onSetDefaultSMDPPlus(result);
            notifyJavaCallback(
              "EuiccLevelController",
              "onSetDefaultSMDPPlus",
              result,
              false
            );
            return returnValue;
          };
      }

      // Hook onError for EUICC operations
      if (EuiccLevelControllerListener.onError) {
        EuiccLevelControllerListener.onError.implementation = function (
          result
        ) {
          log(`[Frida] ### EUICC OPERATION ERROR ### Result: ${result}`);
          const returnValue = this.onError(result);
          notifyJavaCallback("EuiccLevelController", "onError", result, true);
          return returnValue;
        };
      }

      log("[Frida] Hooked EuiccLevelControllerListener methods");
    }
  } catch (err) {
    log("ERROR: [Frida] Error setting up CLI command completion hooks: " + err);
  }

  log(
    "\n[Frida] All hooks applied with CLI completion detection via callback interfaces."
  );
} // End of applyAllHooks

// --- Find the Target ClassLoader and Initiate Hooking ---
let lpaClassLoaderFound = false;
log("[Frida] Enumerating ClassLoaders to find the LPA library loader...");
// Java.enumerateClassLoaders({
//   onMatch: function (loader) {
//     try {
//       // Try to find a known class from the LPA library using this loader
//       if (
//         loader.findClass("es.com.valid.lib_lpa.controler.DownloadControler")
//       ) {
//         log("[Frida] Found target ClassLoader: " + loader);
//         lpaClassLoaderFound = true;
//         // Schedule the application of hooks on the main thread
//         // This can sometimes help if classes are still being initialized.
//         Java.scheduleOnMainThread(function () {
//           log(Java.ClassFactory.get(loader));
//           applyAllHooks(Java.ClassFactory.get(loader));
//         });
//         return "stop"; // Stop enumerating class loaders
//       }
//     } catch (e) {
//       // This loader doesn't have the class, continue searching
//     }
//   },
//   onComplete: function () {
//     log("[Frida] ClassLoader enumeration complete.");
//     if (!lpaClassLoaderFound) {
//       console.error(
//         "[Frida] Target LPA ClassLoader NOT FOUND. Hooks cannot be applied. Ensure LPA classes are loaded before script is fully attached or try delaying hook application."
//       );
//     }
//   },
// });
applyAllHooks();

penumbraClient = chooseLiveClasses("com.penumbraos.sdk.PenumbraClient")[0];
log(`[Frida] Grabbed PenumbraClient ${penumbraClient}`);
