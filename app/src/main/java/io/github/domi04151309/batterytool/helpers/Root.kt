package io.github.domi04151309.batterytool.helpers

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Scanner
import kotlin.collections.HashSet
import kotlin.text.StringBuilder

internal object Root {
    private const val AVAILABILITY_TIMEOUT_MILLIS: Long = 3_000L
    private const val AVAILABILITY_POLL_MILLIS: Long = 100L

    // No root call may block a receiver or the service forever: root prompts that are never
    // answered leave the process alive without producing any output.
    private const val SHELL_TIMEOUT_SECONDS: Long = 5L
    private const val SHELL_TIMEOUT_PER_COMMAND_SECONDS: Long = 1L
    private const val MILLIS_PER_SECOND: Long = 1_000L

    fun request(): Boolean =
        try {
            val process = Runtime.getRuntime().exec("su")
            val output = DataOutputStream(process.outputStream)
            output.writeBytes("echo access granted\n")
            output.writeBytes("exit\n")
            output.flush()
            true
        } catch (exception: IOException) {
            Log.w(this::class.simpleName, exception)
            false
        }

    fun shell(command: String) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            if (!awaitProcess(process, SHELL_TIMEOUT_SECONDS * MILLIS_PER_SECOND)) {
                Log.w(this::class.simpleName, "Root command timed out")
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) Log.w(this::class.simpleName, "Root shell exited with code $exitCode")
            }
        } catch (exception: IOException) {
            Log.w(this::class.simpleName, exception)
        } catch (exception: InterruptedException) {
            Log.w(this::class.simpleName, exception)
            Thread.currentThread().interrupt()
        } finally {
            process?.destroy()
        }
    }

    fun isAvailable(): Boolean {
        var process: Process? = null
        var exitCode = -1
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            var waited = 0L
            // A denied `su` exits immediately with a non-zero code, so the loop has to end on any
            // exit value: polling only while the process is alive keeps the budget at three seconds.
            while (exitCode == -1 && waited < AVAILABILITY_TIMEOUT_MILLIS) {
                try {
                    exitCode = process.exitValue()
                } catch (ignoredException: IllegalThreadStateException) {
                    Thread.sleep(AVAILABILITY_POLL_MILLIS)
                    waited += AVAILABILITY_POLL_MILLIS
                }
            }
        } catch (exception: IOException) {
            Log.w(this::class.simpleName, exception)
        } catch (exception: InterruptedException) {
            Log.w(this::class.simpleName, exception)
            Thread.currentThread().interrupt()
        } finally {
            process?.destroy()
        }
        return exitCode == 0
    }

    fun shell(commands: Array<String>) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            val output = DataOutputStream(process.outputStream)
            for (command in commands) output.writeBytes("$command\n")
            output.writeBytes("exit\n")
            output.flush()
            if (!awaitProcess(process, shellTimeoutSeconds(commands.size) * MILLIS_PER_SECOND)) {
                Log.w(this::class.simpleName, "Root shell timed out")
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) Log.w(this::class.simpleName, "Root shell exited with code $exitCode")
            }
        } catch (exception: IOException) {
            Log.e(this::class.simpleName, exception.toString())
        } catch (exception: InterruptedException) {
            Log.w(this::class.simpleName, exception.toString())
            Thread.currentThread().interrupt()
        } finally {
            process?.destroy()
        }
    }

    fun isRunning(packageName: String): Boolean = getRunning(listOf(packageName)).isNotEmpty()

    /**
     * Returns every package of [packageNames] that currently has a process. The whole list is
     * checked with a single root shell, one `su` process is enough no matter how long the list is.
     */
    fun getRunning(packageNames: Collection<String>): HashSet<String> {
        val running = HashSet<String>()
        if (packageNames.isEmpty()) return running
        val script =
            packageNames.joinToString(separator = "; ") { "pidof '$it' > /dev/null 2>&1 && echo '$it'" }
        for (line in execToString(script).lines()) {
            val packageName = line.trim()
            if (packageName.isNotEmpty()) running.add(packageName)
        }
        return running
    }

    fun getFocusedApps(): HashSet<String> {
        val services = StringBuilder()
        try {
            val inputStream =
                Runtime.getRuntime().exec(
                    arrayOf(
                        "su",
                        "-c",
                        "dumpsys activity activities | " +
                            "grep -E 'CurrentFocus|ResumedActivity|FocusedApp' |  " +
                            "cut -d '{' -f2 | " +
                            "cut -d ' ' -f3 | " +
                            "cut -d '/' -f1",
                    ),
                ).inputStream
            Scanner(inputStream).useDelimiter("\\A").use { scanner ->
                services.append(if (scanner.hasNext()) scanner.next() else "")
            }
        } catch (exception: IOException) {
            Log.e(this::class.simpleName, exception.toString())
        }
        return HashSet(services.lines())
    }

    private fun awaitProcess(
        process: Process,
        timeoutMillis: Long,
    ): Boolean {
        // Process.waitFor(timeout, unit) is only available from API 26, the app supports API 23.
        var waited = 0L
        while (waited < timeoutMillis) {
            try {
                process.exitValue()
                return true
            } catch (ignoredException: IllegalThreadStateException) {
                Thread.sleep(AVAILABILITY_POLL_MILLIS)
                waited += AVAILABILITY_POLL_MILLIS
            }
        }
        return false
    }

    private fun shellTimeoutSeconds(commandCount: Int): Long {
        val perCommandTimeout = commandCount * SHELL_TIMEOUT_PER_COMMAND_SECONDS
        return SHELL_TIMEOUT_SECONDS + perCommandTimeout
    }

    private fun execToString(script: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            if (!awaitProcess(process, SHELL_TIMEOUT_SECONDS * MILLIS_PER_SECOND)) {
                Log.w(this::class.simpleName, "Root command timed out")
                ""
            } else {
                readStream(process.inputStream)
            }
        } catch (exception: IOException) {
            Log.e(this::class.simpleName, exception.toString())
            ""
        } catch (exception: InterruptedException) {
            Log.w(this::class.simpleName, exception.toString())
            Thread.currentThread().interrupt()
            ""
        } finally {
            process?.destroy()
        }
    }

    private fun readStream(inputStream: InputStream): String {
        val output = StringBuilder()
        Scanner(inputStream).useDelimiter("\\A").use { scanner ->
            output.append(if (scanner.hasNext()) scanner.next() else "")
        }
        return output.toString()
    }
}
