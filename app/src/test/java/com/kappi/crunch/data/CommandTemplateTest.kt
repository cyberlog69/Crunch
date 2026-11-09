package com.kappi.crunch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTemplateTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<CommandTemplate>>() {}.type

    @Test
    fun serialization_roundTripSingleTemplate() {
        val template = CommandTemplate(
            id = "template-1",
            name = "Extract High Quality Audio",
            extraArgs = "--audio-quality 0 --embed-thumbnail"
        )
        val json = gson.toJson(listOf(template), listType)
        assertNotNull(json)

        val restored: List<CommandTemplate> = gson.fromJson(json, listType)
        assertEquals(1, restored.size)
        assertEquals("template-1", restored[0].id)
        assertEquals("Extract High Quality Audio", restored[0].name)
        assertEquals("--audio-quality 0 --embed-thumbnail", restored[0].extraArgs)
    }

    @Test
    fun serialization_roundTripMultipleTemplatesWithSpecialCharacters() {
        val templates = listOf(
            CommandTemplate("1", "MKV Remux", "--remux-video mkv"),
            CommandTemplate("2", "Subtitles & Sponsors", "--write-subs --sub-lang \"en,es\" --sponsorblock-remove all"),
            CommandTemplate("3", "Unicode Name 🚀", "--custom-arg='val with spaces'")
        )
        val json = gson.toJson(templates, listType)
        val restored: List<CommandTemplate> = gson.fromJson(json, listType)

        assertEquals(3, restored.size)
        assertEquals("Subtitles & Sponsors", restored[1].name)
        assertEquals("--write-subs --sub-lang \"en,es\" --sponsorblock-remove all", restored[1].extraArgs)
        assertEquals("Unicode Name 🚀", restored[2].name)
    }

    @Test
    fun deserialization_emptyJsonYieldsEmptyList() {
        val restored: List<CommandTemplate> = gson.fromJson("[]", listType)
        assertTrue(restored.isEmpty())
    }
}

