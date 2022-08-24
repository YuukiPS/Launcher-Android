#!/bin/bash

# Make Release
./gradlew assembleRelease

# build https://stackoverflow.com/questions/34005713/difference-between-clean-gradlew-clean
# ./gradlew clean

# apk-mitm GenshinImpact_v3.0.0_ori.apk --wait

# ./metadata-patcher.exe patch C:\Users\Administrator\Desktop\Projek\Tool\metadata-patcher\metadata-patcher

# Fake Signed
# java -jar tool/uber-apk-signer.jar -a app/build/outputs/apk/release/app-release-unsigned.apk

# Patch mod metadata to patch proxy
# java -jar tool/lspatch.jar apk/GenshinImpact_v3.0.0_ori-patched.apk -m app/build/outputs/apk/release/app-release-aligned-debugSigned.apk --v2