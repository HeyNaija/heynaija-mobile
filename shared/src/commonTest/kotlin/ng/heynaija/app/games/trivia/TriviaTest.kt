package ng.heynaija.app.games.trivia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.TriviaRound

class TriviaTest {
    @Test
    fun placesAndTimesReadAsTheWebWritesThem() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "102nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 102).map(::ordinal))
        assertEquals("2.41 s", seconds(2_410))
        assertEquals("0.05 s", seconds(57))
        assertEquals("12.00 s", seconds(12_000))
        assertEquals("0.00 s", seconds(-5))
    }

    @Test
    fun aRoundBeforeItsQuestionOpensHasNoText() {
        // As the API sends it during the countdown: the question is coming, its text is not.
        val json = """{"id":"r1","title":"Anime night","status":"live","startsAt":"2026-11-15T19:00:00.000Z","questions":3,
            "host":{"handle":"sam"},"version":4,"serverTime":"2026-11-15T19:00:01.000Z",
            "you":{"host":false,"canAnswer":true,"points":0},
            "current":{"id":"q1","position":1,"opensAt":"2026-11-15T19:00:03.000Z","closesAt":"2026-11-15T19:00:23.000Z","kind":"choice","prompt":null,"choices":null,"yourAnswer":null},
            "last":null,"standings":[],"yourStanding":null,"final":false}"""
        val round = HeyNaijaApi.json.decodeFromString(TriviaRound.serializer(), json)
        assertEquals(null, round.current?.prompt)
        assertEquals(null, round.current?.choices)
        assertTrue(round.you.canAnswer)
        assertEquals(false, round.over)
        assertEquals(null, round.current?.image)
    }

    @Test
    fun aQuestionsPictureComesWithItOnceItOpens() {
        val picture = """{"src":"/media/posts/m1","alt":"A green-white-green flag","width":900,"height":450,"animated":false}"""
        val json = """{"id":"r1","title":"Flags","status":"live","startsAt":"2026-11-15T19:00:00.000Z","questions":2,
            "host":{"handle":"sam"},"version":5,"serverTime":"2026-11-15T19:00:04.000Z",
            "you":{"host":false,"canAnswer":true,"points":0},
            "current":{"id":"q1","position":1,"opensAt":"2026-11-15T19:00:03.000Z","closesAt":"2026-11-15T19:00:23.000Z","kind":"choice",
              "prompt":"Whose flag is this?","choices":[{"id":1,"text":"Ireland"},{"id":0,"text":"Nigeria"}],"image":$picture,"yourAnswer":null},
            "last":{"id":"q0","position":0,"prompt":"Warm-up","kind":"typed","answer":"Lagos","image":$picture},
            "standings":[],"yourStanding":null,"final":false}"""
        val round = HeyNaijaApi.json.decodeFromString(TriviaRound.serializer(), json)
        val image = round.current?.image
        assertEquals("/media/posts/m1", image?.src)
        assertEquals("A green-white-green flag", image?.alt)
        assertEquals(900, image?.width)
        assertEquals("/media/posts/m1", round.last?.image?.src)
    }
}
