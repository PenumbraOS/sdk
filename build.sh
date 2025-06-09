( cd bridge_priv_rs && cargo ndk -t arm64-v8a -p 28 build --release )
adb shell mkdir /data/local/tmp/bin
adb push bridge_priv_rs/target/aarch64-linux-android/release/bridge_priv_rs /data/local/tmp/bin/bridge_priv
adb shell chmod +x /data/local/tmp/bin/bridge_priv
adb push config/pinitd/* /sdcard/penumbra/etc/pinitd/system/

./gradlew :bridge:installDebug
./gradlew :example:installDebug
