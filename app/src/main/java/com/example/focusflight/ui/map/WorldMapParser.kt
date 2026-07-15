package com.example.focusflight.ui.map

import android.content.Context
import android.graphics.Path
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Locale

data class CountryPath(
    val countryCode: String, // Uppercased ISO code (e.g., "US", "DE")
    val paths: List<Path>
)

object WorldMapParser {
    private var cachedMap: List<CountryPath>? = null

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
            val groupPaths = mutableListOf<Path>()

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
                                    if (currentGroupId != null) {
                                        groupPaths.add(androidPath)
                                    } else {
                                        val id = parser.getAttributeValue(null, "id")
                                        if (id != null) {
                                            countries.add(
                                                CountryPath(
                                                    countryCode = id.uppercase(Locale.US),
                                                    paths = listOf(androidPath)
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
