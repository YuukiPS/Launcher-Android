#!/bin/bash

# Make Release
./gradlew assembleRelease
# ./gradlew clean build https://stackoverflow.com/questions/34005713/difference-between-clean-gradlew-clean
# Fake Signed
# java -jar tool/uber-apk-signer.jar -a app/build/outputs/apk/release/app-release-unsigned.apk

# java -jar tool/lspatch.jar apk/GenshinImpact_v2.8.0_mod.apk -m app/build/outputs/apk/release/app-release-aligned-debugSigned.apk --v2