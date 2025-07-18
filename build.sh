adb shell mkdir -p /sdcard/penumbra/etc/pinitd/system/
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/

( cd bridge-settings/react-app && npm i && npm run build:android )

./gradlew :sdk:publishToMavenLocal :bridge-core:installDebug :bridge-system:installDebug :bridge-shell:installDebug :bridge-settings:installDebug :example:installDebug
echo "Built on $(date '+%Y-%m-%d %H:%M:%S')"