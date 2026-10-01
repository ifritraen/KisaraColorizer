# AGENTS.md — KisaraColorizer
- **Package**: `com.raen.kisaracolorizer` (Entry: `MainActivity.kt`)
- **Check / Build**: `./gradlew :app:compileDebugKotlin` | `./gradlew assembleDebug --offline --build-cache --parallel`

## Wireless ADB
- **Connect**: `$env:ADB_MDNS_OPENSCREEN=1; adb mdns services; adb connect <ip>:<port>`
- **Logcat**: `adb -s <device> logcat -d -s KisaraColorizer AndroidRuntime:E *:E`
- **Pull**: `adb -s <device> pull /data/user/0/com.raen.kisaracolorizer.debug/files/<debug/latest_colorized.png|logs/colorizer.log> .`


