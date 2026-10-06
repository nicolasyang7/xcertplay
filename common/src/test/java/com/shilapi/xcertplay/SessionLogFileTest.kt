package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogFileTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun flushIncludesQueuedEventsInOrder() {
        val file = folder.newFile("xcertplay.log")
        SessionLogFile(file).use { log ->
            log.reset("session")
            repeat(200) { log.append("event-$it") }
            log.flushAndWait()
            assertEquals(listOf("session") + (0 until 200).map { "event-$it" }, file.readLines())
        }
    }

    @Test fun closeDrainsPendingEventsBeforeNextSession() {
        val file = folder.newFile("xcertplay.log")
        val log = SessionLogFile(file)
        log.reset("session")
        repeat(200) { log.append("event-$it") }
        log.close()
        assertEquals(201, file.readLines().size)
        log.append("late event after close")
        assertEquals(201, file.readLines().size)
    }
}
