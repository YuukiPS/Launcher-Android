#!/bin/bash

# Make Release
./gradlew assembleRelease

# Fake Signed
# java -jar tool/uber-apk-signer.jar -a app/build/outputs/apk/release/app-release-unsigned.apk

# java -jar lspatch.jar apk/tes.apk -m app/build/outputs/apk/release/app-release-aligned-debugSigned.apk -l 2