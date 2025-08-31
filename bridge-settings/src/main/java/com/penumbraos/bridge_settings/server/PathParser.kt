package com.penumbraos.bridge_settings.server

object PathParser {
    fun matchPath(pattern: String, actualPath: String): Map<String, String>? {
        val patternSegments = pattern.split('/').filter { it.isNotEmpty() }
        val actualSegments = actualPath.split('/').filter { it.isNotEmpty() }

        if (patternSegments.size != actualSegments.size) {
            return null
        }

        val pathParams = mutableMapOf<String, String>()

        for (i in patternSegments.indices) {
            val patternSegment = patternSegments[i]
            val actualSegment = actualSegments[i]

            if (patternSegment.startsWith('{') && patternSegment.endsWith('}')) {
                val variableName = patternSegment.substring(1, patternSegment.length - 1)
                pathParams[variableName] = actualSegment
            } else if (patternSegment != actualSegment) {
                return null
            }
        }

        return pathParams
    }
}
