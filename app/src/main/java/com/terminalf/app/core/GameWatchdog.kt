package com.terminalf.app.core

/**
 * Cihazda arka planda çalışan "izleyici" betiği: oyun ön plandayken belirli aralıklarla
 * FPS/ısı/CPU örneği kaydeder, oyun kapanınca (ya da kullanıcı başka uygulamaya geçip uzun süre
 * dönmezse) Oyun Modu'nun değiştirdiği her şeyi eski haline getirir. Böylece TerminalF kapansa ya
 * da sistem tarafından öldürülse bile geri alma gerçekleşir.
 *
 * Güvenlik: betiğe yalnızca doğrulanmış paket adı ve [buildRevertSteps]'in ürettiği (değerleri
 * doğrulanmış) komutlar girer. Betik, tanınabilmesi için `TERMINALF_WATCHDOG` işaretini taşır;
 * sonlandırılırken PID dosyasındaki işlemin gerçekten bu betik olduğu doğrulanır (PID yeniden
 * kullanımında başka işlem öldürülmez).
 */

const val kWatchdogDir = "/data/local/tmp"
const val kWatchdogPid = "$kWatchdogDir/terminalf_watchdog.pid"
const val kWatchdogLog = "$kWatchdogDir/terminalf_game.log"
const val kWatchdogMark = "$kWatchdogDir/terminalf_reverted"
const val kWatchdogTag = "TERMINALF_WATCHDOG"

/**
 * Betik şablonu. Yer tutucular: `@PKG@ @PIDF@ @LOG@ @MARK@ @POLL@ @MISS@ @GIVEUP@ @GUARD@
 * @REVERT@ @TEMPLIM@ @TAG@`. `;` yerine satır sonu kullanılır.
 */
private val template = """
# @TAG@
PKG=@PKG@
PIDF=@PIDF@
LOG=@LOG@
MARK=@MARK@
echo ${'$'}${'$'} > ${'$'}PIDF
: > ${'$'}LOG
rm -f ${'$'}MARK
SEEN=0
MISS=0
WAITED=0
HOT=0
GUARD=0
while :
do
  sleep @POLL@
  WAITED=${'$'}((WAITED+@POLL@))
  F=${'$'}(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)
  if echo "${'$'}F" | grep -q "${'$'}PKG"
  then
    SEEN=1
    MISS=0
    LST=${'$'}(dumpsys SurfaceFlinger --list 2>/dev/null | grep "${'$'}PKG" | grep -v Bounds | grep -v Background)
    L=${'$'}(echo "${'$'}LST" | grep -m1 SurfaceView)
    if [ -z "${'$'}L" ]
    then
      L=${'$'}(echo "${'$'}LST" | head -n 1)
    fi
    N=-
    FIRST=-
    LAST=-
    if [ -n "${'$'}L" ]
    then
      D=${'$'}(dumpsys SurfaceFlinger --latency "${'$'}L" 2>/dev/null | tail -n +2 | grep -v 9223372036854775807 | grep -v "^0")
      if [ -n "${'$'}D" ]
      then
        N=${'$'}(echo "${'$'}D" | wc -l)
        set -- ${'$'}(echo "${'$'}D" | head -n 1)
        FIRST=${'$'}2
        set -- ${'$'}(echo "${'$'}D" | tail -n 1)
        LAST=${'$'}2
      fi
    fi
    TS=${'$'}(dumpsys thermalservice 2>/dev/null | grep -m1 "Thermal Status")
    TS=${'$'}{TS##*: }
    P=${'$'}(ls -d /sys/devices/system/cpu/cpufreq/policy* 2>/dev/null | tail -n 1)
    CUR=
    MAX=
    if [ -n "${'$'}P" ]
    then
      CUR=${'$'}(cat ${'$'}P/scaling_cur_freq 2>/dev/null)
      MAX=${'$'}(cat ${'$'}P/cpuinfo_max_freq 2>/dev/null)
    fi
    : "${'$'}{N:=-}" "${'$'}{FIRST:=-}" "${'$'}{LAST:=-}" "${'$'}{TS:=-}" "${'$'}{CUR:=-}" "${'$'}{MAX:=-}"
    echo ${'$'}(date +%s) ${'$'}N ${'$'}FIRST ${'$'}LAST ${'$'}TS ${'$'}CUR ${'$'}MAX >> ${'$'}LOG
    BT=${'$'}(dumpsys battery 2>/dev/null | grep -m1 temperature)
    BT=${'$'}{BT##*: }
    HOTNOW=0
    if [ "${'$'}TS" != "-" ] && [ "${'$'}TS" -ge 2 ] 2>/dev/null
    then
      HOTNOW=1
    fi
    if [ @TEMPLIM@ -gt 0 ] && [ -n "${'$'}BT" ] && [ "${'$'}BT" -ge @TEMPLIM@ ] 2>/dev/null
    then
      HOTNOW=1
    fi
    if [ ${'$'}HOTNOW -eq 1 ]
    then
      HOT=${'$'}((HOT+1))
    else
      HOT=0
    fi
    if [ ${'$'}HOT -ge 2 ] && [ ${'$'}GUARD -eq 0 ]
    then
      GUARD=1
      echo GUARD ${'$'}(date +%s) >> ${'$'}LOG
@GUARD@
    fi
  else
    MISS=${'$'}((MISS+1))
  fi
  if [ ${'$'}SEEN -eq 1 ] && [ ${'$'}MISS -ge @MISS@ ]
  then
    break
  fi
  if [ ${'$'}SEEN -eq 0 ] && [ ${'$'}WAITED -ge @GIVEUP@ ]
  then
    rm -f ${'$'}PIDF
    exit 0
  fi
done
OK=1
@REVERT@
if [ ${'$'}OK -eq 1 ]
then
  echo ok > ${'$'}MARK
else
  echo fail > ${'$'}MARK
fi
rm -f ${'$'}PIDF
""".trimStart('\n')

/**
 * [session]'ı geri alan, izleyici betiğinin tam metni. Paket adı geçersizse boş döner (komut
 * üretilmez).
 *
 * - [pollSeconds]: örnekleme aralığı.
 * - [missLimit]: oyun art arda bu kadar kontrolde ön planda değilse geri alınır (varsayılan
 *   4 x 10 sn = 40 sn; bildirim çubuğunu çekmek geri almayı tetiklemez).
 * - [giveUpSeconds]: oyun hiç ön plana gelmezse izleyici GERİ ALMADAN çıkar (ön plan
 *   algılanamıyorsa mod oyun sırasında kendiliğinden kapanmasın).
 * - [guardTempC]: pil sıcaklığı bu değere (°C) ulaşınca ısı koruması da devreye girer; 0 =
 *   yalnızca ısıl duruma bakılır.
 */
fun buildWatchdogScript(
    session: GameSession,
    pollSeconds: Int = 10,
    missLimit: Int = 4,
    giveUpSeconds: Int = 150,
    guardTempC: Int = 0,
): String {
    if (!isSafePackage(session.packageName)) return ""
    val revert = StringBuilder()
    for (step in buildRevertSteps(session)) {
        // Zincirdeki (`;`) komutlar alt kabukta çalışır; zorunlu adım başarısızsa OK=0 olur ve
        // işaret dosyasına "fail" yazılır.
        if (step.optional) {
            revert.append("( ${step.command} ) >/dev/null 2>&1\n")
        } else {
            revert.append("( ${step.command} ) >/dev/null 2>&1 || OK=0\n")
        }
    }
    // Isı koruması: ısıl durum >= 2 ya da pil sıcaklığı eşikte art arda iki örnekte ise bir kez uygulanır.
    val guard = StringBuilder()
    for (cmd in buildGuardCommands(session)) {
        guard.append("      ( $cmd ) >/dev/null 2>&1\n")
    }
    return template
        .replace("@GUARD@", guard.toString().trimEnd())
        .replace("@TAG@", kWatchdogTag)
        .replace("@PKG@", session.packageName)
        .replace("@PIDF@", kWatchdogPid)
        .replace("@LOG@", kWatchdogLog)
        .replace("@MARK@", kWatchdogMark)
        .replace("@TEMPLIM@", "${guardTempC.coerceIn(0, 70) * 10}")
        .replace("@POLL@", "$pollSeconds")
        .replace("@MISS@", "$missLimit")
        .replace("@GIVEUP@", "$giveUpSeconds")
        .replace("@REVERT@", revert.toString().trimEnd())
}

/** Betiği arka planda (HUP'a duyarsız, çıktısı kapalı) başlatan komut. */
fun buildWatchdogLaunch(script: String): String =
    "nohup sh -c ${shellQuote(script)} </dev/null >/dev/null 2>&1 &"

/**
 * Çalışan izleyiciyi güvenle sonlandırır (PID'in gerçekten izleyici olduğunu doğrulayarak) ve
 * PID dosyasını siler.
 */
fun buildWatchdogKill(): String =
    "P=\$(cat $kWatchdogPid 2>/dev/null); " +
        "if [ -n \"\$P\" ] && grep -q $kWatchdogTag /proc/\$P/cmdline 2>/dev/null; " +
        "then kill \$P; fi; " +
        "rm -f $kWatchdogPid"

/** İzleyici canlı mı ve geri alma işareti var mı? Çıktı: `alive|dead` ve `mark:ok|fail|none`. */
fun buildWatchdogStatus(): String =
    "P=\$(cat $kWatchdogPid 2>/dev/null); " +
        "if [ -n \"\$P\" ] && grep -q $kWatchdogTag /proc/\$P/cmdline 2>/dev/null; " +
        "then echo alive; else echo dead; fi; " +
        "M=\$(cat $kWatchdogMark 2>/dev/null); " +
        "echo \"mark:\${M:-none}\""

/** İşaret dosyasını siler (okunduktan sonra). */
const val kWatchdogClearMark = "rm -f $kWatchdogMark"

/** Günlük dosyasını okur. */
const val kWatchdogReadLog = "cat $kWatchdogLog 2>/dev/null"

/** [buildWatchdogStatus] çıktısının çözümü. */
data class WatchdogStatus(
    val alive: Boolean,
    /** `ok` (geri alındı), `fail` (geri alma başarısız) ya da `none`. */
    val mark: String,
) {
    val revertedOk: Boolean get() = mark == "ok"
    val revertFailed: Boolean get() = mark == "fail"
}

fun parseWatchdogStatus(output: String): WatchdogStatus {
    val lines = outputLines(output)
    val alive = lines.contains("alive")
    var mark = "none"
    for (line in lines) {
        if (line.startsWith("mark:")) {
            val v = line.substring(5).trim()
            if (v == "ok" || v == "fail") mark = v
        }
    }
    return WatchdogStatus(alive = alive, mark = mark)
}
