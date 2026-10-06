package com.shilapi.xcertplay

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class SessionLogFile(val file: File) : Closeable {
    private val lock = Any()
    private val writerExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_PENDING_LINES),
        { task -> Thread(task, "xcertplay-log-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )
    private var output: BufferedOutputStream? = null
    private var bytesWritten = 0L
    private var closed = false
    private val lineFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun reset(header: String) {
        synchronized(lock) {
            if (closed) return
            file.parentFile?.mkdirs()
            output?.close()
            output = file.outputStream().buffered()
            bytesWritten = 0L
            writeLine(header)
        }
    }

    fun append(line: String) = enqueue { line }

    fun appendTimestamped(message: String, timestampMillis: Long) = enqueue {
        "${lineFormatter.format(Date(timestampMillis))}  $message"
    }

    fun flushAndWait() {
        val flush: Future<*> = synchronized(lock) {
            if (closed) return
            writerExecutor.submit {
                try {
                    output?.flush()
                } catch (_: IOException) {
                    runCatching { output?.close() }
                    output = null
                }
            }
        }
        try {
            flush.get(2L, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while flushing diagnostic logs", interrupted)
        } catch (failure: Exception) {
            flush.cancel(false)
            throw IOException("Could not flush diagnostic logs within 2 seconds", failure)
        }
    }

    private fun enqueue(line: () -> String) {
        synchronized(lock) {
            if (closed) return
            writerExecutor.execute {
                try {
                    writeLine(line())
                } catch (_: IOException) {
                    runCatching { output?.close() }
                    output = null
                }
            }
        }
    }

    private fun writeLine(line: String) {
        var bytes = line.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size >= MAX_BYTES) {
            var start = bytes.size - (MAX_BYTES - 1)
            while (start < bytes.size && bytes[start].toInt() and 0xc0 == 0x80) start++
            bytes = bytes.copyOfRange(start, bytes.size)
        }
        if (bytesWritten + bytes.size + 1 > MAX_BYTES) {
            output?.close()
            val previousLog = File(file.parentFile, "${file.nameWithoutExtension}.previous.log")
            runCatching { previousLog.delete() }
            if (file.exists() && file.length() > 0L) {
                runCatching { file.renameTo(previousLog) }
            }
            output = file.outputStream().buffered()
            bytesWritten = 0L
        }
        val activeOutput = output ?: return
        activeOutput.write(bytes)
        activeOutput.write('\n'.code)
        activeOutput.flush()
        bytesWritten += bytes.size + 1
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            writerExecutor.execute {
                runCatching { output?.close() }
                output = null
            }
            writerExecutor.shutdown()
        }
        // Drain queued writes before another Activity opens the same file, but never
        // wait indefinitely on a slow or unavailable external storage device.
        try {
            if (!writerExecutor.awaitTermination(2L, TimeUnit.SECONDS)) writerExecutor.shutdownNow()
        } catch (interrupted: InterruptedException) {
            writerExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
        const val MAX_PENDING_LINES = 1024
    }
}
