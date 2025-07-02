adb shell mkdir -p /sdcard/penumbra/etc/pinitd/system/
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/

./gradlew :sdk:publishToMavenLocal
./gradlew :bridge-core:installDebug
./gradlew :bridge-system:installDebug
./gradlew :example:installDebug
