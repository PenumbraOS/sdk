import { loadClass } from "./classloader";

const MAX_ITERATIONS = 200;
const BF25_TAG = "BF25";
const HEADER_LENGTH_NIBBLES = 4;

const LPA_TAG_ICCID = "5A";
const LPA_TAG_SERVICE_PROVIDER_NAME = "91";
const LPA_TAG_PROFILE_NAME = "92";
const LPA_TAG_ICON_TYPE = "93";
const LPA_TAG_ICON = "94";
const LPA_TAG_PROFILE_CLASS = "95";
const LPA_TAG_PROFILE_POLICY_RULES = "99";
const LPA_TAG_NOTIFICATION_CONFIG = "B6";
const LPA_TAG_OPERATOR_ID = "B7";

export const createFillStoreMetadataRequestImplementation = () => {
  return function (this: any, str: string, i: number): any {
    console.log(
      "[Frida] ----> FE.fillStoreMetadataRequest entered with str_len:" +
        (str ? str.length : "null") +
        ", initial_offset_i:" +
        i
    );
    const startTime = new Date().getTime();

    const StoreMetadataRequestClass = loadClass(
      "es.com.valid.lib_lpa.dataClasses.StoreMetadataRequest"
    );
    const ControlerUtil = loadClass("es.com.valid.lib_lpa.controler.Util");

    if (!StoreMetadataRequestClass) {
      throw new Error("StoreMetadataRequest class not found");
    }
    if (!ControlerUtil) {
      throw new Error("ControlerUtil class not found");
    }

    let storeMetadataRequest = StoreMetadataRequestClass.$new();

    if (!str || str.length < 4) {
      console.error("[Frida] Input string is too short.");
      throw new Error("Input string too short for BF25 processing.");
    }

    let topLevelTag = str.substring(i, i + 4).toUpperCase();
    if (topLevelTag !== BF25_TAG) {
      console.error(
        "[Frida] Expected BF25 tag at offset " +
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

    let headerLenNibbles = HEADER_LENGTH_NIBBLES;
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
    let endOfBF25DataNibbles = dataStartOffsetInNibbles + lengthOfBF25Bytes * 2;

    console.log(
      "[Frida] BF25 tag found. Declared length: " +
        lengthOfBF25Bytes +
        " bytes. Data starts at nibble-offset: " +
        dataStartOffsetInNibbles +
        ", ends at nibble-offset: " +
        endOfBF25DataNibbles +
        " (relative to start of string)."
    );

    // Current parsing offset within BF25 data content
    let currentParseOffsetNibbles = dataStartOffsetInNibbles;

    let iterationCount = 0;

    while (
      currentParseOffsetNibbles < endOfBF25DataNibbles &&
      iterationCount < MAX_ITERATIONS
    ) {
      iterationCount++;
      if (currentParseOffsetNibbles + 2 > str.length) {
        console.error(
          "[Frida] Offset " +
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
      console.log(
        "[Frida] Loop: " +
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
        if (currentTag === LPA_TAG_ICCID) {
          let iccidObj = this.fillIccid(str, currentParseOffsetNibbles);
          tagCompletelyConsumedNibbles = iccidObj.getSize();
          storeMetadataRequest.setIccid(iccidObj);
        } else if (currentTag === LPA_TAG_SERVICE_PROVIDER_NAME) {
          let hexStrObj = this.fillHexString(str, currentParseOffsetNibbles);
          tagCompletelyConsumedNibbles = hexStrObj.getSize();
          storeMetadataRequest.setServiceProviderName(hexStrObj);
        } else if (currentTag === LPA_TAG_PROFILE_NAME) {
          let hexStrObj = this.fillHexString(str, currentParseOffsetNibbles);
          tagCompletelyConsumedNibbles = hexStrObj.getSize();
          storeMetadataRequest.setProfileName(hexStrObj);
        } else if (currentTag === LPA_TAG_ICON_TYPE) {
          let iconTypeObj = this.fillIconType(str, currentParseOffsetNibbles);
          tagCompletelyConsumedNibbles = iconTypeObj.getSize();
          storeMetadataRequest.setIconType(iconTypeObj);
        } else if (currentTag === LPA_TAG_ICON) {
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
        } else if (currentTag === LPA_TAG_PROFILE_CLASS) {
          let profileClassObj = this.fillProfileClass(
            str,
            currentParseOffsetNibbles
          );
          tagCompletelyConsumedNibbles = profileClassObj.getSize();
          storeMetadataRequest.setProfileClass(profileClassObj);
        } else if (currentTag === LPA_TAG_PROFILE_POLICY_RULES) {
          let pprIdsObj = this.fillPprIds(str, currentParseOffsetNibbles);
          tagCompletelyConsumedNibbles = pprIdsObj.getSize();
          storeMetadataRequest.setProfilePolicyRules(pprIdsObj);
        } else if (currentTag === LPA_TAG_NOTIFICATION_CONFIG) {
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
        } else if (currentTag === LPA_TAG_OPERATOR_ID) {
          let operatorIdObj = this.fillOperatorId(
            str,
            currentParseOffsetNibbles
          );
          tagCompletelyConsumedNibbles = operatorIdObj.getSize();
          storeMetadataRequest.setProfileOwner(operatorIdObj);
        } else {
          console.warn(
            "[Frida] Unhandled Tag: " +
              currentTag +
              " at offset " +
              currentParseOffsetNibbles +
              ". Attempting to skip."
          );
          if (currentParseOffsetNibbles + 4 > str.length) {
            console.error(
              "[Frida] Not enough data to parse length of unhandled tag " +
                currentTag +
                ". Breaking."
            );
            break;
          }
          let lenOfUnhandledBytes = ControlerUtil.getBERLengthInInt(
            str,
            currentParseOffsetNibbles + 2
          );
          let lenFieldSizeUnhandled = ControlerUtil.getBERLengthSizeInNibbles(
            str,
            currentParseOffsetNibbles + 2
          );
          tagCompletelyConsumedNibbles =
            2 + lenFieldSizeUnhandled + lenOfUnhandledBytes * 2;
          console.log(
            "[Frida] Skipped " +
              tagCompletelyConsumedNibbles +
              " nibbles for unhandled tag " +
              currentTag
          );
        }

        if (tagCompletelyConsumedNibbles > 0) {
          currentParseOffsetNibbles += tagCompletelyConsumedNibbles;
        } else {
          console.error(
            "[Frida] Tag " +
              currentTag +
              " was not processed correctly (consumed 0 nibbles). Breaking loop to prevent infinite loop."
          );
          break;
        }
      } catch (e) {
        console.error(
          "[Frida] Error processing tag " +
            currentTag +
            " at offset " +
            currentParseOffsetNibbles +
            ": " +
            e
        );
        console.error("[Frida] Stack: " + (e as Error).stack);
        try {
          if (currentParseOffsetNibbles + 4 <= str.length) {
            let lenOfUnhandledBytes = ControlerUtil.getBERLengthInInt(
              str,
              currentParseOffsetNibbles + 2
            );
            let lenFieldSizeUnhandled = ControlerUtil.getBERLengthSizeInNibbles(
              str,
              currentParseOffsetNibbles + 2
            );
            let skippedNibbles =
              2 + lenFieldSizeUnhandled + lenOfUnhandledBytes * 2;
            console.warn(
              "[Frida] Attempting to skip " +
                skippedNibbles +
                " nibbles after error on tag " +
                currentTag
            );
            currentParseOffsetNibbles += skippedNibbles;
          } else {
            console.error(
              "[Frida] Not enough data to skip tag " +
                currentTag +
                " after error. Breaking."
            );
            break;
          }
        } catch (skipError) {
          console.error(
            "[Frida] Error while trying to skip tag " +
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
        "[Frida] Max iterations (" +
          MAX_ITERATIONS +
          ") reached for fillStoreMetadataRequest loop."
      );
    }
    const duration = new Date().getTime() - startTime;
    console.log(
      "[Frida] <---- FE.fillStoreMetadataRequest exited. Duration: " +
        duration +
        "ms. Processed " +
        iterationCount +
        " tags."
    );
    return storeMetadataRequest;
  };
};
