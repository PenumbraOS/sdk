adb shell /data/local/tmp/bin/pinitd-cli stop bridge-settings-service
adb shell /data/local/tmp/bin/pinitd-cli stop bridge-shell-service
adb shell /data/local/tmp/bin/pinitd-cli stop bridge-system-service
adb shell /data/local/tmp/bin/pinitd-cli restart bridge-service

adb shell /data/local/tmp/bin/pinitd-cli start bridge-settings-service
adb shell /data/local/tmp/bin/pinitd-cli start bridge-shell-service
adb shell /data/local/tmp/bin/pinitd-cli start bridge-system-service