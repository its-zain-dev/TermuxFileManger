package dev.itszain.termuxfilemanager.features

import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.shellQuote

fun shellPath(path: String): String = when {
    path == "~" || path == "~/" -> "\"\$HOME\""
    path.startsWith("~/") -> "\"\$HOME\"/" + shellQuote(path.removePrefix("~/"))
    else -> shellQuote(path)
}

data class ToolAction(
    val title: Int,
    val group: Int,
    val command: String = "",
    val prefix: String = "",
    val hint: Int = R.string.package_name,
    val interactive: Boolean = false
)

val toolCatalog = listOf(
    // Termux Packages
    ToolAction(R.string.package_search, R.string.packages, prefix = "pkg search"),
    ToolAction(R.string.package_install, R.string.packages, prefix = "pkg install -y"),
    ToolAction(R.string.package_remove, R.string.packages, prefix = "pkg uninstall -y"),
    ToolAction(R.string.package_show, R.string.packages, prefix = "pkg show"),
    ToolAction(R.string.package_index, R.string.packages, "pkg update"),
    ToolAction(R.string.package_upgrade, R.string.packages, "pkg update && pkg upgrade -y"),
    ToolAction(R.string.package_list, R.string.packages, "pkg list-installed"),
    ToolAction(R.string.package_clean, R.string.packages, "pkg autoclean && apt autoremove -y"),

    // Git Toolkit
    ToolAction(R.string.git_tool_status, R.string.git_tools, "git status --short --branch && git diff --stat"),
    ToolAction(R.string.git_tool_log, R.string.git_tools, "git log --oneline --graph --decorate -n 20"),
    ToolAction(R.string.git_tool_branches, R.string.git_tools, "git branch -a -vv"),
    ToolAction(R.string.git_tool_pull, R.string.git_tools, "git pull --ff-only"),
    ToolAction(R.string.git_tool_diff, R.string.git_tools, "git diff"),
    ToolAction(R.string.git_tool_clone, R.string.git_tools, prefix = "git clone", hint = R.string.https_url),

    // Languages & Package Managers
    ToolAction(R.string.python_packages, R.string.languages, prefix = "python -m pip install"),
    ToolAction(R.string.python_pip_list, R.string.languages, "python -m pip list"),
    ToolAction(R.string.node_packages, R.string.languages, prefix = "npm install"),
    ToolAction(R.string.rust_packages, R.string.languages, prefix = "cargo install"),
    ToolAction(R.string.ruby_packages, R.string.languages, prefix = "gem install"),
    ToolAction(R.string.go_packages, R.string.languages, prefix = "go install"),
    ToolAction(R.string.php_packages, R.string.languages, prefix = "composer require"),
    ToolAction(R.string.lua_packages, R.string.languages, prefix = "luarocks install"),

    // Network & Servers
    ToolAction(R.string.net_http_server, R.string.network_tools, "python -m http.server 8080", interactive = true),
    ToolAction(R.string.net_ports, R.string.network_tools, "ss -tulpn 2>/dev/null || netstat -tulpn 2>/dev/null || echo 'Install iproute2 or net-tools'"),
    ToolAction(R.string.net_ip, R.string.network_tools, "ip -brief addr 2>/dev/null || ifconfig 2>/dev/null"),
    ToolAction(R.string.net_ssh_keygen, R.string.network_tools, "ssh-keygen -t ed25519", interactive = true),

    // Diagnostics
    ToolAction(
        R.string.runtime_inventory, R.string.diagnostics,
        "for c in python node npm bun rustc cargo ruby gem go php composer lua luarocks java javac clang gcc g++ make cmake git curl tar unzip ssh opencode; do p=\$(command -v \"\$c\" 2>/dev/null) && printf '%-12s %s\\n' \"\$c\" \"\$p\"; done"
    ),
    ToolAction(R.string.system_information, R.string.diagnostics, "uname -a; echo \"Android: \$(getprop ro.build.version.release) (API \$(getprop ro.build.version.sdk))\"; df -h; head -n 5 /proc/meminfo"),
    ToolAction(R.string.storage_information, R.string.diagnostics, "df -h; du -sh \"\$HOME\" \"\$PREFIX\" 2>/dev/null"),
    ToolAction(R.string.running_processes, R.string.diagnostics, "ps aux")
)

fun runnerFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "py" -> "python ${shellPath(path)}"
    "js", "mjs", "cjs" -> "node ${shellPath(path)}"
    "ts" -> "npx tsx ${shellPath(path)}"
    "sh", "bash" -> "bash ${shellPath(path)}"
    "rb" -> "ruby ${shellPath(path)}"
    "php" -> "php ${shellPath(path)}"
    "lua" -> "lua ${shellPath(path)}"
    "go" -> "go run ${shellPath(path)}"
    "rs" -> "rustc ${shellPath(path)} -o /tmp/tfm_run && /tmp/tfm_run"
    "c" -> "cc ${shellPath(path)} -o /tmp/tfm_run && /tmp/tfm_run"
    "cpp", "cc", "cxx" -> "c++ ${shellPath(path)} -o /tmp/tfm_run && /tmp/tfm_run"
    else -> shellPath(path)
}
