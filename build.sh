adb shell mkdir /data/local/tmp/bin
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/

./gradlew :bridge-core:installDebug
./gradlew :bridge-system:installDebug
./gradlew :example:installDebug
