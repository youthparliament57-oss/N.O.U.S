package com.roshan.persona.telemetry

import org.junit.Test
import kotlin.system.measureTimeMillis
import java.io.File
import org.json.JSONObject

class AuditLogPruningTest {

    @Test
    fun benchmarkPruning() {
        val AUDIT_LOG_FILENAME = "audit_log.jsonl"
        val RETENTION_DAYS = 90
        val filesDir = File(System.getProperty("java.io.tmpdir"), "audit_bench_dir")
        filesDir.mkdirs()

        val logFile = File(filesDir, "audit_log.jsonl")
        logFile.delete()

        val now = System.currentTimeMillis()
        logFile.bufferedWriter().use { writer ->
            for (i in 0..100000) {
                val ts = if (i < 50000) now - 100L * 24 * 60 * 60 * 1000L else now
                writer.write("""{"id":$i,"ts":$ts,"type":"SKILL_INVOCATION","action":"Test","correlationId":"corr-$i","outcome":"SUCCESS","metadata":{}}""")
                writer.write("\n")
            }
        }

        val cutoffMs = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

        // --- OLD WAY ---
        val timeOld = measureTimeMillis {
            val kept = mutableListOf<String>()
            logFile.useLines { lines ->
                lines.forEach { line ->
                    try {
                        val json = JSONObject(line)
                        val ts = json.optLong("ts", 0L)
                        if (ts >= cutoffMs) {
                            kept.add(line)
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            val tmp = File(filesDir, "$AUDIT_LOG_FILENAME.tmp1")
            tmp.printWriter(Charsets.UTF_8).use { writer ->
                kept.forEach { writer.println(it) }
            }
        }
        println("OLD WAY took: ${timeOld}ms")

        // --- NEW WAY (Streaming + JSON parsing) ---
        val timeNew1 = measureTimeMillis {
            val tmp = File(filesDir, "$AUDIT_LOG_FILENAME.tmp2")
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                logFile.useLines { lines ->
                    lines.forEach { line ->
                        try {
                            val json = JSONObject(line)
                            val ts = json.optLong("ts", 0L)
                            if (ts >= cutoffMs) {
                                writer.write(line)
                                writer.write("\n")
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        println("NEW WAY (Streaming + JSON parsing) took: ${timeNew1}ms")

        // --- NEW WAY (Streaming + Fast parsing) ---
        val tsRegex = """"ts"\s*:\s*(\d+)""".toRegex()
        val timeNew2 = measureTimeMillis {
            val tmp = File(filesDir, "$AUDIT_LOG_FILENAME.tmp3")
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                logFile.useLines { lines ->
                    lines.forEach { line ->
                        try {
                            val match = tsRegex.find(line)
                            if (match != null) {
                                val ts = match.groupValues[1].toLong()
                                if (ts >= cutoffMs) {
                                    writer.write(line)
                                    writer.write("\n")
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        println("NEW WAY (Streaming + Fast Regex) took: ${timeNew2}ms")

        // --- NEW WAY (Streaming + Fast IndexOf) ---
        val timeNew3 = measureTimeMillis {
            val tmp = File(filesDir, "$AUDIT_LOG_FILENAME.tmp4")
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                logFile.useLines { lines ->
                    lines.forEach { line ->
                        try {
                            val tsIndex = line.indexOf("\"ts\":")
                            if (tsIndex != -1) {
                                val start = tsIndex + 5
                                var end = start
                                while (end < line.length && line[end].isDigit()) {
                                    end++
                                }
                                if (start < end) {
                                    val ts = line.substring(start, end).toLong()
                                    if (ts >= cutoffMs) {
                                        writer.write(line)
                                        writer.write("\n")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        println("NEW WAY (Streaming + Fast IndexOf) took: ${timeNew3}ms")
        logFile.delete()
        filesDir.delete()
    }
}
