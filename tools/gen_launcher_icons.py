#!/usr/bin/env python3
"""Hazır logo renkleri için başlatıcı (ana ekran) simgelerini ve manifest alias'larını üretir.
Kaynak: app/src/main/java/com/terminalf/app/core/Palettes.kt içindeki LogoStyle.presets.
Çalıştır: python3 tools/gen_launcher_icons.py"""
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main")
pal = open(os.path.join(ROOT, "java/com/terminalf/app/core/Palettes.kt"), encoding="utf-8").read()
styles = []
for m in re.finditer(r'LogoStyle\("(\w+)",\s*"[^"]*",\s*listOf\(([^)]*)\)\)', pal):
    cols = re.findall(r"0xFF([0-9A-Fa-f]{6})", m.group(2))
    if cols:
        styles.append((m.group(1), ["#" + c.upper() for c in cols]))

CHEVRON = "M318,316 L548,512 L318,708"
CURSOR = ("M632,664 L756,664 A34,34 0 0 1 790,698 L790,734 A34,34 0 0 1 756,768 "
          "L632,768 A34,34 0 0 1 598,734 L598,698 A34,34 0 0 1 632,664 Z")


def fg_xml(cols):
    if len(cols) == 1:
        stroke = f'android:strokeColor="{cols[0]}"'
        gradient = ""
    else:
        stroke = ""
        gradient = (
            '        <aapt:attr name="android:strokeColor">\n'
            '            <gradient android:type="linear" android:startX="250" android:startY="250"\n'
            '                android:endX="620" android:endY="780">\n'
            f'                <item android:offset="0" android:color="{cols[0]}"/>\n'
            f'                <item android:offset="1" android:color="{cols[1]}"/>\n'
            "            </gradient>\n"
            "        </aapt:attr>\n"
        )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Otomatik üretildi: tools/gen_launcher_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="1024" android:viewportHeight="1024">
    <group android:scaleX="1.1" android:scaleY="1.1" android:pivotX="512" android:pivotY="512">
        <path android:pathData="{CHEVRON}" {stroke}
            android:strokeWidth="132" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000">
{gradient}        </path>
        <path android:pathData="{CURSOR}" android:fillColor="#F5F5F5"/>
    </group>
</vector>
"""


def w(path, text):
    full = os.path.join(ROOT, "res", path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, "w", encoding="utf-8").write(text)


aliases = []
for key, cols in styles:
    if key == "white":
        icon = "@mipmap/ic_launcher"          # varsayılan simge (hazır PNG + tek renkli simge)
    else:
        icon = f"@mipmap/ic_launcher_{key}"
        w(f"drawable/ic_launcher_fg_{key}.xml", fg_xml(cols))
        w(f"mipmap-anydpi-v26/ic_launcher_{key}.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_fg_{key}"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
""")
        # Android 7.x (API 24-25): uyarlanabilir simge yok; koyu zemin + logo katmanı.
        w(f"mipmap-anydpi/ic_launcher_{key}.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/ic_launcher_background"/>
    <item android:drawable="@drawable/ic_launcher_fg_{key}"/>
</layer-list>
""")
    cls = "Launcher" + "".join(p[:1].upper() + p[1:] for p in key.split("_"))
    enabled = "true" if key == "white" else "false"
    aliases.append(f"""        <activity-alias
            android:name=".{cls}"
            android:enabled="{enabled}"
            android:exported="true"
            android:icon="{icon}"
            android:label="TerminalF"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
""")

man_path = os.path.join(ROOT, "AndroidManifest.xml")
man = open(man_path, encoding="utf-8").read()
man = re.sub(r"\n?\s*<!-- ALIAS-BASLA.*?<!-- ALIAS-BITIS -->\n?", "\n", man, flags=re.S)
block = "        <!-- ALIAS-BASLA: tools/gen_launcher_icons.py üretir; elle düzenlemeyin -->\n" + "\n".join(aliases) + "        <!-- ALIAS-BITIS -->\n"
man = man.replace("        <provider", block + "\n        <provider", 1)
open(man_path, "w", encoding="utf-8").write(man)
print("Simge sayısı:", len(styles), [k for k, _ in styles])
