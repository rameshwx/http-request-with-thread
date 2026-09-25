package com.rameshwx.httprequestwiththread.data.json

/** A deliberately small JSON reader/writer so the demo has no JSON dependency. */
sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue {
        operator fun get(name: String): JsonValue? = fields[name]
        fun string(name: String): String? = (fields[name] as? Str)?.value
        fun number(name: String): Double? = (fields[name] as? Num)?.value
        fun bool(name: String): Boolean? = (fields[name] as? Bool)?.value
    }
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

object MiniJson {
    fun parse(text: String): JsonValue = Parser(text).parse()
    fun stringify(value: JsonValue): String = buildString { appendValue(value) }
    fun obj(vararg entries: Pair<String, JsonValue>): JsonValue.Obj = JsonValue.Obj(mapOf(*entries))
    fun str(value: String): JsonValue = JsonValue.Str(value)
    fun num(value: Int): JsonValue = JsonValue.Num(value.toDouble())
    fun nullValue(): JsonValue = JsonValue.Null

    private fun StringBuilder.appendValue(value: JsonValue) {
        when (value) {
            is JsonValue.Obj -> {
                append('{')
                value.fields.entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    appendQuoted(entry.key); append(':'); appendValue(entry.value)
                }
                append('}')
            }
            is JsonValue.Arr -> {
                append('[')
                value.values.forEachIndexed { index, item -> if (index > 0) append(','); appendValue(item) }
                append(']')
            }
            is JsonValue.Str -> appendQuoted(value.value)
            is JsonValue.Num -> append(value.value.toString().removeSuffix(".0"))
            is JsonValue.Bool -> append(value.value)
            JsonValue.Null -> append("null")
        }
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private class Parser(private val source: String) {
        private var index = 0
        fun parse(): JsonValue {
            skipSpace()
            val value = readValue()
            skipSpace()
            require(index == source.length) { "Unexpected characters after JSON value at $index" }
            return value
        }
        private fun readValue(): JsonValue {
            skipSpace()
            require(index < source.length) { "Unexpected end of JSON" }
            return when (source[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't' -> { literal("true"); JsonValue.Bool(true) }
                'f' -> { literal("false"); JsonValue.Bool(false) }
                'n' -> { literal("null"); JsonValue.Null }
                '-', in '0'..'9' -> readNumber()
                else -> error("Invalid JSON value at $index")
            }
        }
        private fun readObject(): JsonValue.Obj {
            expect('{'); skipSpace()
            val fields = linkedMapOf<String, JsonValue>()
            if (take('}')) return JsonValue.Obj(fields)
            while (true) {
                skipSpace(); require(index < source.length && source[index] == '"') { "Expected object key at $index" }
                val key = readString(); skipSpace(); expect(':')
                fields[key] = readValue(); skipSpace()
                if (take('}')) break
                expect(',')
            }
            return JsonValue.Obj(fields)
        }
        private fun readArray(): JsonValue.Arr {
            expect('['); skipSpace()
            val values = mutableListOf<JsonValue>()
            if (take(']')) return JsonValue.Arr(values)
            while (true) {
                values += readValue(); skipSpace()
                if (take(']')) break
                expect(',')
            }
            return JsonValue.Arr(values)
        }
        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        require(index < source.length) { "Incomplete string escape" }
                        when (val escaped = source[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "Incomplete unicode escape" }
                                val code = source.substring(index, index + 4).toIntOrNull(16)
                                    ?: error("Invalid unicode escape at $index")
                                out.append(code.toChar()); index += 4
                            }
                            else -> error("Invalid string escape at ${index - 1}")
                        }
                    }
                    c.code < 0x20 -> error("Unescaped control character in string")
                    else -> out.append(c)
                }
            }
            error("Unterminated string")
        }
        private fun readNumber(): JsonValue.Num {
            val start = index
            take('-')
            if (!take('0')) {
                require(index < source.length && source[index] in '1'..'9') { "Invalid number at $index" }
                while (index < source.length && source[index].isDigit()) index++
            }
            if (take('.')) {
                require(index < source.length && source[index].isDigit()) { "Invalid fraction at $index" }
                while (index < source.length && source[index].isDigit()) index++
            }
            if (index < source.length && source[index] in "eE") {
                index++; if (index < source.length && source[index] in "+-") index++
                require(index < source.length && source[index].isDigit()) { "Invalid exponent at $index" }
                while (index < source.length && source[index].isDigit()) index++
            }
            return JsonValue.Num(source.substring(start, index).toDouble())
        }
        private fun literal(expected: String) {
            require(source.startsWith(expected, index)) { "Expected $expected at $index" }
            index += expected.length
        }
        private fun expect(char: Char) { require(take(char)) { "Expected '$char' at $index" } }
        private fun take(char: Char): Boolean = if (index < source.length && source[index] == char) { index++; true } else false
        private fun skipSpace() { while (index < source.length && source[index] in " \t\r\n") index++ }
    }
}
