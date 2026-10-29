package dev.itszain.termuxfilemanager

import android.content.Context
import dev.itszain.termuxfilemanager.core.CommandSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Compatibility boundary for the existing workspace format; callers run on IO. */
object LegacyCommands {
    @JvmStatic fun run(context: Context, command: String, workdir: String?, label: String?, background: Boolean): Int = runBlocking(Dispatchers.IO) {
        context.graph().commands.submit(CommandSpec(command, workdir ?: "~/", label ?: "TFM", "settings", background, kind = "workspace")).toInt()
    }
}
