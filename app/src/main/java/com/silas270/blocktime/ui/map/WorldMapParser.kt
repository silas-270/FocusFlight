package com.silas270.blocktime.ui.map

import android.content.Context
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Locale

data class CountryPath(
    val countryCode: String, // Uppercased ISO code (e.g., "US", "DE")
    val paths: List<androidx.compose.ui.graphics.Path>
)

object WorldMapParser {
    /**
     * Volatile because [warm] now parses this from a background thread at app start while the
     * Passport and Flight Search may read it from theirs. A benign double-parse was always
     * possible and remains so (the result is identical either way); what this rules out is a
     * reader seeing a half-published list.
     */
    @Volatile
    private var cachedMap: List<CountryPath>? = null

    /**
     * Parses the world map ahead of the first screen that needs it. The SVG is ~73KB and yields
     * roughly a thousand vector paths, and it used to be parsed lazily on whichever screen the
     * pilot opened first - which was usually the Passport, on the very load already doing the most
     * work. Doing it once at startup costs nothing visible and takes it off that path entirely.
     */
    fun warm(context: Context) {
        parseWorldMap(context)
    }

    fun parseWorldMap(context: Context): List<CountryPath> {
        cachedMap?.let { return it }

        val countries = mutableListOf<CountryPath>()
        try {
            val inputStream: InputStream = context.assets.open("world-map.svg")
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentGroupId: String? = null
            val groupPaths = mutableListOf<androidx.compose.ui.graphics.Path>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "g") {
                            val id = parser.getAttributeValue(null, "id")
                            if (id != null && id != "world-map") {
                                currentGroupId = id
                                groupPaths.clear()
                            }
                        } else if (name == "path") {
                            val d = parser.getAttributeValue(null, "d")
                            if (d != null) {
                                try {
                                    val androidPath = PathParser.createPathFromPathData(d)
                                    val composePath = androidPath.asComposePath()
                                    if (currentGroupId != null) {
                                        groupPaths.add(composePath)
                                    } else {
                                        val id = parser.getAttributeValue(null, "id")
                                        if (id != null) {
                                            countries.add(
                                                CountryPath(
                                                    countryCode = id.uppercase(Locale.US),
                                                    paths = listOf(composePath)
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("WorldMapParser", "Failed to parse path data", e)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "g") {
                            if (currentGroupId != null) {
                                countries.add(
                                    CountryPath(
                                        countryCode = currentGroupId.uppercase(Locale.US),
                                        paths = ArrayList(groupPaths)
                                    )
                                    )
                                currentGroupId = null
                                groupPaths.clear()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
            cachedMap = countries
        } catch (e: Exception) {
            android.util.Log.e("WorldMapParser", "Error parsing SVG", e)
        }
        return countries
    }
}
