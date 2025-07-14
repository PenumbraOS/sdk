adb shell mkdir -p /sdcard/penumbra/etc/pinitd/system/
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/

( cd bridge-settings/react-app && npm i && npm run build:android )

./gradlew :sdk:publishToMavenLocal
./gradlew :bridge-core:installDebug
./gradlew :bridge-system:installDebug
./gradlew :bridge-shell:installDebug
./gradlew :bridge-settings:installDebug
./gradlew :example:installDebug
