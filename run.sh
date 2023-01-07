#!/bin/bash

echo "Create Proxy APK"
./gradlew assembleRelease

echo "Add Fake Signed"
java -jar tool/uber-apk-signer.jar -a app/build/outputs/apk/release/app-release-unsigned.apk

install_to_phone=false

# 安卓3.3本地服.apk 4124 official
# 原神3.3-代码服-玩仿官服的不要安装.apk work download data

if ($install_to_phone); then
    # file_apk="apk/official/origin.apk"
    file_apk="apk/not_touch/安卓3.3本地服.apk"
    file_final="apk/final/YuukiPS_V2.apk"
    # file_out="apk/out/origin-361-lspatched.apk"
    file_out="apk/out/安卓3.3本地服-361-lspatched.apk"
    file_our="app/build/outputs/apk/release/app-release-aligned-debugSigned.apk"
    file_cn="apk/mod_cn/xfk233.genshinproxy.apk"

    echo "Tried Patching Mod APK with Proxy APK"
    # java -jar tool/lspatch.jar $file_apk -m $file_our -m $file_cn -o apk/out -f
    java -jar tool/lspatch.jar $file_apk -m $file_our -o apk/out -f || echo "Failed to Patch Mod APK with Proxy APK"

    echo "Rename file..."
    mv $file_out $file_final || echo -e "Failed to rename file\nFile: $file_out\nTo: $file_final\nNot found" && exit 1

    # adb install -r "C:\Users\Akbar Yahya\Desktop\Projek\YuukiPS\Launcher-Android\app\build\outputs\apk\release\app-release-aligned-debugSigned.apk"
    echo "Trying to install on phone (Final)"
    adb install -r "$(PWD)/$file_final"
fi