package com.linernotes.app.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShanlingSyncLinkProtocolTest {

    @Test
    fun testBuildAndParseFrameHeader() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame = ShanlingSyncLinkProtocol.buildFrame(
            messageId = 1234,
            commandId = ShanlingSyncLinkProtocol.SL_ENABLE_PLAYTIME_NOTIFY_REQ,
            payload = payload,
            priority = 0x40
        )

        assertEquals(12 + 4, frame.size)
        val (msgId, cmdId, len) = ShanlingSyncLinkProtocol.parseHeader(frame)
        assertEquals(1234, msgId)
        assertEquals(ShanlingSyncLinkProtocol.SL_ENABLE_PLAYTIME_NOTIFY_REQ, cmdId)
        assertEquals(4, len)
    }

    @Test
    fun testPlayTimeNotifyDecoding() {
        // Build simulated PlayTimeNotify:
        // Field 1 (playtime): 75 seconds (0x4B) -> tag 0x08, val 0x4B
        // Field 2 (duration): 245 seconds (245 = 0xF5, 0x01 in varint) -> tag 0x10, val 0xF5, 0x01
        val payload = byteArrayOf(
            0x08.toByte(), 0x4B.toByte(),
            0x10.toByte(), 0xF5.toByte(), 0x01.toByte()
        )

        val result = ShanlingSyncLinkProtocol.decodePlayTimeNotify(payload)
        assertEquals(75, result.playtimeSeconds)
        assertEquals(245, result.durationSeconds)
    }

    @Test
    fun testPlayStatusDecoding() {
        // Field 1 (playstatus = 0 for PLAY) -> tag 0x08, val 0x00
        // Field 2 (current_position = 0 for Track 1) -> tag 0x10, val 0x00
        // Field 3 (total_songs = 12 tracks) -> tag 0x18, val 0x0C
        val payload = byteArrayOf(
            0x08.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(),
            0x18.toByte(), 0x0C.toByte()
        )

        val result = ShanlingSyncLinkProtocol.decodePlayStatus(payload)
        assertTrue(result.isPlaying)
        assertEquals(0, result.queueIndex)
        assertEquals(1, result.trackNumber)
        assertEquals(12, result.totalSongs)
    }

    @Test
    fun testPlayStatusDecodingPaused() {
        // Field 1 (playstatus = 5 for PAUSE) -> tag 0x08, val 0x05
        // Field 2 (current_position = 1 for Track 2) -> tag 0x10, val 0x01
        // Field 3 (total_songs = 10 tracks) -> tag 0x18, val 0x0A
        val payload = byteArrayOf(
            0x08.toByte(), 0x05.toByte(),
            0x10.toByte(), 0x01.toByte(),
            0x18.toByte(), 0x0A.toByte()
        )

        val result = ShanlingSyncLinkProtocol.decodePlayStatus(payload)
        assertFalse(result.isPlaying)
        assertEquals(1, result.queueIndex)
        assertEquals(2, result.trackNumber)
        assertEquals(10, result.totalSongs)
    }

    @Test
    fun testPlayStatusDecodingStopped() {
        // Field 1 (playstatus = 3 for STOP) -> tag 0x08, val 0x03
        val payload = byteArrayOf(
            0x08.toByte(), 0x03.toByte()
        )

        val result = ShanlingSyncLinkProtocol.decodePlayStatus(payload)
        assertFalse(result.isPlaying)
    }

    @Test
    fun testPlayControlEncoding() {
        val playReq = ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PLAY_SONG)
        assertEquals(2, playReq.size)
        assertEquals(0x08.toByte(), playReq[0])
        assertEquals(0x00.toByte(), playReq[1])

        val pauseReq = ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PAUSE_SONG)
        assertEquals(2, pauseReq.size)
        assertEquals(0x08.toByte(), pauseReq[0])
        assertEquals(0x05.toByte(), pauseReq[1])

        val nextReq = ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_NEXT_SONG)
        assertEquals(2, nextReq.size)
        assertEquals(0x08.toByte(), nextReq[0])
        assertEquals(0x01.toByte(), nextReq[1])

        val prevReq = ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PREV_SONG)
        assertEquals(2, prevReq.size)
        assertEquals(0x08.toByte(), prevReq[0])
        assertEquals(0x02.toByte(), prevReq[1])
    }

    @Test
    fun testCdPlayQueueEncoding() {
        val trackReq = ShanlingSyncLinkProtocol.encodeCdPlayQueue(5)
        assertEquals(2, trackReq.size)
        assertEquals(0x08.toByte(), trackReq[0])
        assertEquals(0x05.toByte(), trackReq[1])
    }
}
