package com.everett.motionextractor

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Captures errors and crashes, and shows a copyable dialog.
 */
object ErrorReporter {

    private val logBuilder = StringBuilder()

    fun init(application: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildReport(throwable)
            logBuilder.append(report)
            showErrorDialog(application, report)
            // Give user time to see/copy before killing.
            Handler(Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            }, 3000)
        }
    }

    fun log(message: String) {
        logBuilder.appendLine(message)
    }

    fun getLogs(): String = logBuilder.toString()

    fun showErrorDialog(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            val textView = TextView(context).apply {
                text = message
                setPadding(40, 40, 40, 40)
                isVerticalScrollBarEnabled = true
                movementMethod = ScrollingMovementMethod()
                setTextIsSelectable(true)
            }

            AlertDialog.Builder(context)
                .setTitle("错误日志（可长按复制）")
                .setView(textView)
                .setPositiveButton("复制") { _, _ ->
                    copyToClipboard(context, message)
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .setCancelable(false)
                .show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Error Log", text))
    }

    private fun buildReport(throwable: Throwable): String {
        return buildString {
            appendLine("=== Motion Extractor Error Report ===")
            appendLine("Time: ${java.util.Date()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS?.contentToString()}")
            appendLine()
            appendLine(throwable.stackTraceToString())
            appendLine("=====================================")
        }
    }
}
