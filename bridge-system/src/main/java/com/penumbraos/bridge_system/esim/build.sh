( cd frida && npm run build )
cp frida/dist/index.js app/src/main/jniLibs/arm64-v8a/libgadget.script.so
./gradlew installDebug