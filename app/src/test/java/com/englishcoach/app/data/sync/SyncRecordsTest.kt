package com.englishcoach.app.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRecordsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cardSurvivesRoundTripThroughServerJson() {
        val card = CardDto("course:A1:hello:0", "course:A1:hello", 0, 1_700_000_000_000, 15.7, 3.2, 2, 1, 2, 1_699_000_000_000, 1_700_000_000_500)
        assertEquals(card, json.decodeFromJsonElement<CardDto>(json.encodeToJsonElement(card)))
    }

    @Test
    fun laterChangeWinsAndEqualDoesNotOverwrite() {
        assertTrue(SyncRepository.newer(2, 1))
        assertFalse(SyncRepository.newer(1, 1))
        assertFalse(SyncRepository.newer(1, 2))
    }

    @Test
    fun wordsApplyBeforeTheirCardsAndCardsBeforeAnswers() {
        val order = Kind.ORDER
        assertTrue(order.indexOf(Kind.WORD) < order.indexOf(Kind.CARD))
        assertTrue(order.indexOf(Kind.CARD) < order.indexOf(Kind.REVIEW))
    }
}
