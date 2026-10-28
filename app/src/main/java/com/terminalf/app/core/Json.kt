package com.terminalf.app.core

/**
 * Küçük, bağımlılıksız JSON okuyucu/yazıcı.
 *
 * Neden `org.json` değil? `core` ve `state` paketleri Android'e bağlı değildir; böylece birim
 * testleri bir cihaz/emülatör ya da sahte `android.jar` olmadan düz JVM'de çalışır. Burada
 * yalnızca uygulamanın kendi yapılandırması ve kayıtları (tweaks.json, oyun ayarları,
 * oyun oturumu anlık görüntüsü) için gereken kadarı vardır.
 *
 * Tür eşlemesi: nesne -> `Map<String, Any?>`, dizi -> `List<Any?>`, metin -> `String`,
 * tam sayı -> `Long`, ondalıklı sayı -> `Double`, `true/false` -> `Boolean`, `null` -> `null`.
 */
object Json {
    class ParseException(message: String) : Exception(message)

    /** [text]'i çözer. Geçersizse [ParseException] fırlatır. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val value = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Unexpected character: ${p.pos}")
        return value
    }

    /** [parse] gibi, ama hata yerine `null` döner. */
    fun parseOrNull(text: String): Any? = try {
        parse(text)
    } catch (_: ParseException) {
        null
    }

    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> writeDouble(sb, value.toDouble())
            is Double -> writeDouble(sb, value)
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            is Enum<*> -> writeString(sb, value.name)
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeDouble(sb: StringBuilder, v: Double) {
        // JSON'da NaN/Infinity yoktur.
        if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            if (atEnd()) throw ParseException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw ParseException("Unexpected character '$c' @$pos")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Invalid value @$pos")
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                if (peek() != '"') throw ParseException("Expected key @$pos")
                val key = readString()
                skipWs()
                if (peek() != ':') throw ParseException("Expected ':' @$pos")
                pos++
                skipWs()
                map[key] = readValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw ParseException("Expected ',' or '}' @$pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                list.add(readValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw ParseException("Expected ',' or ']' @$pos")
                }
            }
        }

        private fun peek(): Char {
            if (atEnd()) throw ParseException("Unexpected end")
            return s[pos]
        }

        private fun readString(): String {
            pos++ // açılış tırnağı
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Invalid escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Invalid \\u")
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw ParseException("Invalid \\u$hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Invalid escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && s[pos].isDigit()) pos++
            var isDouble = false
            if (pos < s.length && s[pos] == '.') {
                isDouble = true
                pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            if (!isDouble) text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: throw ParseException("Invalid number '$text'")
        }
    }
}

// ---- Tür güvenli okuma yardımcıları (Dart'taki `json['x'] as T?` karşılıkları) ----

@Suppress("UNCHECKED_CAST")
fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

fun Any?.asList(): List<Any?>? = this as? List<Any?>

fun Any?.asNum(): Number? = this as? Number

fun Map<String, Any?>.str(key: String): String? = this[key] as? String

fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

fun Map<String, Any?>.dbl(key: String): Double? = (this[key] as? Number)?.toDouble()
