adb shell mkdir -p /sdcard/penumbra/etc/pinitd/system/enabled
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_service.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_settings.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_shell_service.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_system_service.unit

( cd bridge-settings/react-app && npm i && npm run build:android )
( cd frida && npm run build )
cp frida/dist/index.js bridge-system/src/main/jniLibs/arm64-v8a/libgadget.script.so

if ! [ -f bridge-system/src/main/jniLibs/arm64-v8a/libgadget.so ]; then
  curl -L -o bridge-system/src/main/jniLibs/arm64-v8a/libgadget.temp https://github.com/frida/frida/releases/download/16.7.19/frida-gadget-16.7.19-android-arm64.so.xz
  xz --decompress bridge-system/src/main/jniLibs/arm64-v8a/libgadget.temp
  rm bridge-system/src/main/jniLibs/arm64-v8a/libgadget.temp
  mv bridge-system/src/main/jniLibs/arm64-v8a/frida-gadget-16.7.19-android-arm64.so bridge-system/src/main/jniLibs/arm64-v8a/libgadget.so
fi

./gradlew :sdk:publishToMavenLocal :bridge-core:installDebug :bridge-system:installDebug :bridge-shell:installDebug :bridge-settings:installDebug :example:installDebug
echo "Built on $(date '+%Y-%m-%d %H:%M:%S')"