package dev.itszain.termuxfilemanager.features

import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.shellQuote

fun shellPath(path: String): String = when {
    path == "~" || path == "~/" -> "\"\$HOME\""
    path.startsWith("~/") -> "\"\$HOME\"/" + shellQuote(path.removePrefix("~/"))
    else -> shellQuote(path)
}
data class ToolAction(val title: Int, val group: Int, val command: String = "", val prefix: String = "", val hint: Int = R.string.package_name)
val toolCatalog = listOf(
    ToolAction(R.string.package_search, R.string.packages, prefix = "pkg search"),
    ToolAction(R.string.package_install, R.string.packages, prefix = "pkg install -y"),
    ToolAction(R.string.package_index, R.string.packages, "pkg update"),
    ToolAction(R.string.package_upgrade, R.string.packages, "pkg update && pkg upgrade -y"),
    ToolAction(R.string.package_list, R.string.packages, "pkg list-installed"),
    ToolAction(R.string.python_packages, R.string.languages, prefix = "python -m pip install"),
    ToolAction(R.string.node_packages, R.string.languages, prefix = "npm install"),
    ToolAction(R.string.rust_packages, R.string.languages, prefix = "cargo install"),
    ToolAction(R.string.ruby_packages, R.string.languages, prefix = "gem install"),
    ToolAction(R.string.go_packages, R.string.languages, prefix = "go install"),
    ToolAction(R.string.php_packages, R.string.languages, prefix = "composer require"),
    ToolAction(R.string.lua_packages, R.string.languages, prefix = "luarocks install"),
    ToolAction(R.string.runtime_inventory, R.string.diagnostics,
        "for c in python node npm rustc cargo ruby gem go php composer lua luarocks java javac clang gcc g++ make cmake git curl tar unzip; do command -v \"\$c\" || true; done"),
    ToolAction(R.string.system_information, R.string.diagnostics, "uname -a; getprop ro.build.version.release; df -h; cat /proc/meminfo | head -n 5"),
    ToolAction(R.string.storage_information, R.string.diagnostics, "df -h; du -sh \"\$HOME\" \"\$PREFIX\" 2>/dev/null")
)
fun runnerFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "py" -> "python ${shellPath(path)}"
    "js", "mjs", "cjs" -> "node ${shellPath(path)}"
    "sh", "bash" -> "bash ${shellPath(path)}"
    "rb" -> "ruby ${shellPath(path)}"
    "php" -> "php ${shellPath(path)}"
    "lua" -> "lua ${shellPath(path)}"
    "go" -> "go run ${shellPath(path)}"
    else -> shellPath(path)
}
