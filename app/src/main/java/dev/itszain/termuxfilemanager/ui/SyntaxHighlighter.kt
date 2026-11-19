package dev.itszain.termuxfilemanager.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Lightweight, zero-dependency syntax highlighter for common developer languages. */
object SyntaxHighlighter {
    private const val MAX_HIGHLIGHT_CHARS = 80_000

    fun detectLanguage(pathOrHint: String): String {
        val clean = pathOrHint.trim().lowercase()
        val ext = if ('.' in clean || '/' in clean) clean.substringAfterLast('/').substringAfterLast('.', clean.substringAfterLast('/')) else clean
        return when (ext) {
            "kt", "kts", "kotlin" -> "kotlin"
            "java" -> "java"
            "py", "pyw", "python" -> "python"
            "js", "jsx", "mjs", "cjs", "javascript", "ts", "tsx", "typescript" -> "js"
            "json", "jsonc" -> "json"
            "sh", "bash", "zsh", "shell", "fish" -> "shell"
            "xml", "html", "htm", "svg" -> "xml"
            "c", "h", "cpp", "cc", "cxx", "hpp", "rs", "rust", "go", "golang", "dart", "swift" -> "cstyle"
            "yaml", "yml", "toml", "ini", "properties", "conf", "cfg" -> "config"
            "sql" -> "sql"
            "css", "scss", "less" -> "css"
            "md", "markdown" -> "markdown"
            "gradle", "groovy" -> "kotlin"
            else -> "plain"
        }
    }

    private val KEYWORDS = mapOf(
        "kotlin" to setOf("package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else", "when", "for", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw", "null", "true", "false", "this", "super", "is", "in", "as", "by", "private", "public", "protected", "internal", "open", "override", "abstract", "sealed", "data", "enum", "companion", "suspend", "inline", "reified", "const", "lateinit", "init", "get", "set"),
        "java" to setOf("package", "import", "class", "interface", "enum", "extends", "implements", "public", "private", "protected", "static", "final", "abstract", "synchronized", "volatile", "transient", "void", "boolean", "byte", "short", "int", "long", "float", "double", "char", "if", "else", "switch", "case", "default", "for", "while", "do", "break", "continue", "return", "try", "catch", "finally", "throw", "throws", "new", "null", "true", "false", "this", "super", "instanceof"),
        "python" to setOf("def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue", "pass", "try", "except", "finally", "raise", "with", "as", "import", "from", "lambda", "yield", "async", "await", "and", "or", "not", "in", "is", "None", "True", "False", "global", "nonlocal", "del", "assert", "match", "case"),
        "js" to setOf("function", "const", "let", "var", "class", "extends", "import", "export", "from", "default", "return", "if", "else", "switch", "case", "for", "while", "do", "break", "continue", "try", "catch", "finally", "throw", "new", "typeof", "instanceof", "in", "of", "async", "await", "yield", "null", "undefined", "true", "false", "this", "super", "interface", "type", "enum", "public", "private", "readonly"),
        "shell" to setOf("if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return", "exit", "local", "export", "set", "unset", "readonly", "shift", "source", "echo", "printf", "cd", "pwd", "test", "true", "false"),
        "cstyle" to setOf("fn", "let", "mut", "pub", "struct", "enum", "impl", "trait", "mod", "use", "match", "func", "package", "import", "type", "interface", "go", "defer", "chan", "select", "range", "int", "long", "short", "char", "void", "bool", "float", "double", "const", "static", "extern", "typedef", "sizeof", "include", "define", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "true", "false", "null", "nullptr", "nil", "self", "Self", "async", "await", "where"),
        "sql" to setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "DROP", "ALTER", "JOIN", "LEFT", "RIGHT", "INNER", "ON", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "ORDER", "BY", "GROUP", "LIMIT", "AS", "DISTINCT", "COUNT", "DESC", "ASC")
    )

    private val JSON_PATTERN = Regex("""("(?:\.|[^"\n])*"\s*:)|("(?:\.|[^"\n])*")|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)""")
    private val XML_PATTERN = Regex("""(<!--[\s\S]*?-->)|(</?[A-Za-z_][\w:.-]*|/?>)|([A-Za-z_][\w:.-]*(?=\s*=))|("[^"]*"|'[^']*')""")
    private val MD_PATTERN = Regex("""(?m)^(#{1,6}\s+.*)$|(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(^\s*[-*+]\s)""")
    private val CONFIG_PATTERN = Regex("""(?m)(#.*$)|^(\s*[A-Za-z0-9_.-]+)(\s*[:=])|("(?:\.|[^"\n])*"|'[^'\n]*')|\b(true|false|null|yes|no)\b|\b(\d+(?:\.\d+)?)\b""")

    fun highlight(code: String, language: String, colors: TfmExtendedColors): AnnotatedString {
        if (code.isEmpty() || language == "plain" || code.length > MAX_HIGHLIGHT_CHARS) {
            return AnnotatedString(code)
        }
        return when (language) {
            "json" -> highlightJson(code, colors)
            "xml" -> highlightXml(code, colors)
            "markdown" -> highlightMarkdown(code, colors)
            "config" -> highlightConfig(code, colors)
            else -> highlightCodeTokens(code, language, colors)
        }
    }

    fun visualTransformation(language: String, colors: TfmExtendedColors, enabled: Boolean): VisualTransformation {
        if (!enabled || language == "plain") return VisualTransformation.None
        return VisualTransformation { text ->
            TransformedText(highlight(text.text, language, colors), OffsetMapping.Identity)
        }
    }

    private fun highlightJson(code: String, c: TfmExtendedColors) = buildAnnotatedString {
        append(code)
        JSON_PATTERN.findAll(code).forEach { m ->
            val style = when {
                m.groups[1] != null -> SpanStyle(color = c.syntaxProperty)
                m.groups[2] != null -> SpanStyle(color = c.syntaxString)
                m.groups[3] != null -> SpanStyle(color = c.syntaxKeyword, fontWeight = FontWeight.Medium)
                m.groups[4] != null -> SpanStyle(color = c.syntaxNumber)
                else -> null
            }
            if (style != null) addStyle(style, m.range.first, m.range.last + 1)
        }
    }

    private fun highlightXml(code: String, c: TfmExtendedColors) = buildAnnotatedString {
        append(code)
        XML_PATTERN.findAll(code).forEach { m ->
            val style = when {
                m.groups[1] != null -> SpanStyle(color = c.syntaxComment, fontStyle = FontStyle.Italic)
                m.groups[2] != null -> SpanStyle(color = c.syntaxKeyword)
                m.groups[3] != null -> SpanStyle(color = c.syntaxProperty)
                m.groups[4] != null -> SpanStyle(color = c.syntaxString)
                else -> null
            }
            if (style != null) addStyle(style, m.range.first, m.range.last + 1)
        }
    }

    private fun highlightMarkdown(code: String, c: TfmExtendedColors) = buildAnnotatedString {
        append(code)
        MD_PATTERN.findAll(code).forEach { m ->
            val style = when {
                m.groups[1] != null -> SpanStyle(color = c.syntaxFunction, fontWeight = FontWeight.Bold)
                m.groups[2] != null -> SpanStyle(color = c.syntaxString)
                m.groups[3] != null -> SpanStyle(color = c.syntaxKeyword, fontWeight = FontWeight.Bold)
                m.groups[4] != null -> SpanStyle(color = c.syntaxNumber, fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) addStyle(style, m.range.first, m.range.last + 1)
        }
    }

    private fun highlightConfig(code: String, c: TfmExtendedColors) = buildAnnotatedString {
        append(code)
        CONFIG_PATTERN.findAll(code).forEach { m ->
            when {
                m.groups[1] != null -> addStyle(SpanStyle(color = c.syntaxComment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                m.groups[2] != null -> addStyle(SpanStyle(color = c.syntaxProperty), m.groups[2]!!.range.first, m.groups[2]!!.range.last + 1)
                m.groups[4] != null -> addStyle(SpanStyle(color = c.syntaxString), m.range.first, m.range.last + 1)
                m.groups[5] != null -> addStyle(SpanStyle(color = c.syntaxKeyword), m.range.first, m.range.last + 1)
                m.groups[6] != null -> addStyle(SpanStyle(color = c.syntaxNumber), m.range.first, m.range.last + 1)
            }
        }
    }

    private fun highlightCodeTokens(code: String, language: String, c: TfmExtendedColors) = buildAnnotatedString {
        append(code)
        val kw = KEYWORDS[language] ?: emptySet()
        val hashComment = language in setOf("python", "shell")
        val sqlMode = language == "sql"
        val n = code.length
        var i = 0
        while (i < n) {
            val ch = code[i]
            // Line comments
            if ((hashComment && ch == '#') ||
                (!hashComment && ch == '/' && i + 1 < n && code[i + 1] == '/') ||
                (sqlMode && ch == '-' && i + 1 < n && code[i + 1] == '-')
            ) {
                val start = i
                while (i < n && code[i] != '\n') i++
                addStyle(SpanStyle(color = c.syntaxComment, fontStyle = FontStyle.Italic), start, i)
                continue
            }
            // Block comments /* ... */
            if (!hashComment && ch == '/' && i + 1 < n && code[i + 1] == '*') {
                val start = i
                i += 2
                while (i + 1 < n && !(code[i] == '*' && code[i + 1] == '/')) i++
                i = (i + 2).coerceAtMost(n)
                addStyle(SpanStyle(color = c.syntaxComment, fontStyle = FontStyle.Italic), start, i)
                continue
            }
            // Strings
            if (ch == '"' || ch == '\'' || (ch == '`' && language in setOf("js", "shell"))) {
                val start = i
                val quote = ch
                i++
                while (i < n) {
                    if (code[i] == '\\' && i + 1 < n) { i += 2; continue }
                    if (code[i] == quote) { i++; break }
                    if (code[i] == '\n' && quote != '`') break
                    i++
                }
                addStyle(SpanStyle(color = c.syntaxString), start, i)
                continue
            }
            // Annotations / shell variables
            if ((ch == '@' && language in setOf("kotlin", "java", "python")) || (ch == '$' && language == "shell")) {
                val start = i
                i++
                if (i < n && code[i] == '{') {
                    while (i < n && code[i] != '}' && code[i] != '\n') i++
                    if (i < n && code[i] == '}') i++
                } else {
                    while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                }
                if (i > start + 1) addStyle(SpanStyle(color = c.syntaxProperty), start, i)
                continue
            }
            // Numbers
            if (ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_')) {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '.' || code[i] == '_')) i++
                addStyle(SpanStyle(color = c.syntaxNumber), start, i)
                continue
            }
            // Identifiers / keywords / types / functions
            if (ch.isLetter() || ch == '_') {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                val lookup = if (sqlMode) word.uppercase() else word
                when {
                    lookup in kw -> addStyle(SpanStyle(color = c.syntaxKeyword, fontWeight = FontWeight.Medium), start, i)
                    word[0].isUpperCase() && word.any { it.isLowerCase() } -> addStyle(SpanStyle(color = c.syntaxType), start, i)
                    i < n && code[i] == '(' -> addStyle(SpanStyle(color = c.syntaxFunction), start, i)
                }
                continue
            }
            i++
        }
    }
}
