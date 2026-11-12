#!/bin/zsh
# Build the signed Ruzgar APK on this Mac and publish it as a GitHub release (Obtainium picks it up).
# Usage: ./derle_apk.sh <versionCode>   e.g. ./derle_apk.sh 2
set -e
cd "${0:A:h}"
K=${1:?versionCode required}
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export IMZA_DOSYA="$PWD/../android_imza/imza.p12"
export IMZA_SIFRE="$(cat ../android_imza/sifre.txt)"
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle --no-daemon -q assembleRelease -PsurumKodu=$K
V="1.0.$K"; cp app/build/outputs/apk/release/app-release.apk "$TMPDIR/ruzgar-$V.apk"
gh release create "v$V" "$TMPDIR/ruzgar-$V.apk" -R bekoas/ruzgar-android --title "Ruzgar $V" --notes "Build $V"
echo "released v$V"
