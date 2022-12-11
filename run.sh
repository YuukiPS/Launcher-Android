#!/bin/bash

# Make Release
./gradlew assembleRelease

# Fake Signed
java -jar tool/uber-apk-signer.jar -a app/build/outputs/apk/release/app-release-unsigned.apk

# Patch mod metadata to patch proxy
java -jar tool/lspatch.jar apk/cn4rel.apk -m app/build/outputs/apk/release/app-release-aligned-debugSigned.apk

# Send to phone
# adb install -r "C:\Users\Akbar Yahya\Desktop\Projek\YuukiPS\Launcher-Android\app\build\outputs\apk\release\app-release-aligned-debugSigned.apk"
# adb install -r "C:\Users\Akbar Yahya\Desktop\Projek\YuukiPS\Launcher-Android\cn4rel-308-lspatched.apk"