#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk" ]]; then
  echo "Defina ANDROID_SDK_ROOT ou ANDROID_HOME" >&2
  exit 1
fi

build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
platform="$(find "$sdk/platforms" -mindepth 1 -maxdepth 1 -type d -name 'android-*' | sort -V | tail -n 1)"
if [[ -z "$build_tools" || -z "$platform" ]]; then
  echo "Instale uma plataforma Android e o Android Build Tools" >&2
  exit 1
fi

out="$root/build"
classes="$out/classes"
assets="$root/src/main/assets"
mkdir -p "$classes" "$out/gen" "$out/obj" "$out/dex" "$out/dex-unpacked"

native_runner=()
if [[ "$(uname -m)" == "aarch64" ]] && file "$build_tools/aapt2" | grep -q 'x86-64'; then
  native_runner=(qemu-x86_64-static)
fi

"${native_runner[@]}" "$build_tools/aapt2" compile --dir "$root/src/main/res" -o "$out/res.zip"
link_args=(--auto-add-overlay -I "$platform/android.jar" \
  --manifest "$root/src/main/AndroidManifest.xml" -R "$out/res.zip" \
  --java "$out/gen" -o "$out/unsigned.apk")
if [[ -d "$assets" ]]; then link_args=(-A "$assets" "${link_args[@]}"); fi
"${native_runner[@]}" "$build_tools/aapt2" link "${link_args[@]}"

javac -source 8 -target 8 -encoding UTF-8 \
  -classpath "$platform/android.jar" \
  -d "$classes" \
  $(find "$root/src/main/java" -name '*.java' -print) \
  $(find "$out/gen" -name '*.java' -print)

"$build_tools/d8" --lib "$platform/android.jar" --output "$out/dex" $(find "$classes" -name '*.class' -print)
cp "$out/unsigned.apk" "$out/aligned.apk"
zip -9 -q -j "$out/aligned.apk" "$out/dex/classes.dex"
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f 4 "$out/aligned.apk" "$out/CodexWeb-unsigned-aligned.apk"
else
  echo "Aviso: zipalign não está disponível no Termux; assinando APK não alinhado." >&2
  cp "$out/aligned.apk" "$out/CodexWeb-unsigned-aligned.apk"
fi

if [[ -n "${ANDROID_DEBUG_KEYSTORE:-}" ]]; then
  keystore="$ANDROID_DEBUG_KEYSTORE"
else
  keystore="$out/debug.keystore"
  keytool -genkeypair -v -keystore "$keystore" -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1 || true
fi

"$build_tools/apksigner" sign --ks "$keystore" --ks-pass pass:android \
  --out "$out/CodexWeb-debug.apk" "$out/CodexWeb-unsigned-aligned.apk"
"$build_tools/apksigner" verify "$out/CodexWeb-debug.apk"
echo "APK: $out/CodexWeb-debug.apk"
