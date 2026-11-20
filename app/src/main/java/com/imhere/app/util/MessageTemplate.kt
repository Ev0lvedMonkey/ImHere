package com.imhere.app.util

/**
 * Renders a user message template.
 *
 * Supported variables: `{address}`, `{latitude}`, `{longitude}`, `{map_link}`,
 * `{time}`. A variable without a value is replaced with an empty string, and the
 * whole line that becomes empty or turns into a dangling label ("Время:") is
 * removed, so the message never contains broken fragments.
 */
object MessageTemplate {

    const val ADDRESS = "address"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val MAP_LINK = "map_link"
    const val TIME = "time"

    val variables: List<String> = listOf(ADDRESS, LATITUDE, LONGITUDE, MAP_LINK, TIME)

    private val repeatedSpaces = Regex("[ \\t]{2,}")
    private val separatorsOnly = Regex("^[\\s,;:.\\-–—/()]*$")

    /** True when [template] actually uses `{address}`. */
    fun containsAddress(template: String): Boolean = template.contains("{$ADDRESS}")

    fun render(template: String, values: Map<String, String?>): String {
        var text = template
        values.forEach { (key, value) ->
            text = text.replace("{$key}", value.orEmpty())
        }
        return text.lines()
            .map { line -> line.replace(repeatedSpaces, " ").trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !separatorsOnly.matches(line) &&
                    !line.endsWith(":")
            }
            .map { line -> line.trimEnd(',', ';', ':', '-', ' ') }
            .joinToString("\n")
            .trim()
    }
}
