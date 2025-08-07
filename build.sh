adb shell mkdir -p /sdcard/penumbra/etc/pinitd/system/enabled
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_service.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_settings.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_shell_service.unit
adb shell touch /sdcard/penumbra/etc/pinitd/system/enabled/bridge_system_service.unit

( cd bridge-settings/react-app && npm i && npm run build:android )

./gradlew :sdk:publishToMavenLocal :bridge-core:installDebug :bridge-system:installDebug :bridge-shell:installDebug :bridge-settings:installDebug :example:installDebug
echo "Built on $(date '+%Y-%m-%d %H:%M:%S')"