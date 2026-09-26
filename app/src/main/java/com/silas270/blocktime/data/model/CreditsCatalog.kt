package com.silas270.blocktime.data.model

/** One third-party source: what it is, whose it is, under which license, and where it lives. */
data class Credit(
    val title: String,
    val detail: String,
    val license: String,
    val url: String,
    /** A license text that has to ship with the app (MIT's permission notice), shown verbatim. */
    val notice: String? = null
)

data class CreditSection(val title: String, val credits: List<Credit>)

/**
 * Everything the app shows that it didn't make, for the Settings → Credits screen. A new map
 * source, data set, model or artwork belongs here in the same change that adds it; a network map
 * style also needs its on-map line ([CARTO_MAP_CREDIT] / [ESRI_MAP_CREDIT]), since CARTO and Esri
 * require the credit on the map itself while their tiles are showing.
 */
object CreditsCatalog {

    /** The in-flight map credit while the Standard (CARTO dark) style is showing. */
    const val CARTO_MAP_CREDIT = "© CARTO © OpenStreetMap contributors"

    /** The in-flight map credit while the Satellite + Terrain (Esri imagery) style is showing. */
    const val ESRI_MAP_CREDIT = "Powered by Esri · Esri, Vantor, Earthstar Geographics"

    private const val TABLER_MIT_NOTICE =
        "MIT License · Copyright (c) 2020-2026 Paweł Kuna\n\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
            "software and associated documentation files (the \"Software\"), to deal in the Software " +
            "without restriction, including without limitation the rights to use, copy, modify, " +
            "merge, publish, distribute, sublicense, and/or sell copies of the Software, and to " +
            "permit persons to whom the Software is furnished to do so, subject to the following " +
            "conditions:\n\n" +
            "The above copyright notice and this permission notice shall be included in all copies " +
            "or substantial portions of the Software.\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, " +
            "INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A " +
            "PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT " +
            "HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF " +
            "CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE " +
            "OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."

    val sections: List<CreditSection> = listOf(
        CreditSection(
            "MAPS",
            listOf(
                Credit(
                    title = "Dark map",
                    detail = "Basemap tiles © CARTO",
                    license = "CARTO BASEMAPS",
                    url = "https://carto.com/attribution/"
                ),
                Credit(
                    title = "OpenStreetMap",
                    detail = "Map data © OpenStreetMap contributors",
                    license = "ODBL 1.0",
                    url = "https://www.openstreetmap.org/copyright"
                ),
                Credit(
                    title = "Satellite imagery",
                    detail = "Powered by Esri. Source: Esri, Vantor, Earthstar Geographics, and the GIS User Community",
                    license = "ESRI TERMS OF USE",
                    url = "https://goto.arcgisonline.com/maps/World_Imagery"
                ),
                Credit(
                    title = "Terrain",
                    detail = "Terrain Tiles by Mapzen on AWS, from ArcticDEM, Geoscience Australia, " +
                        "data.gv.at, Government of Canada, Copernicus (EU-DEM), NOAA ETOPO1, INEGI, " +
                        "Land Information New Zealand, Kartverket, UK Environment Agency and USGS " +
                        "(3DEP, SRTM, GMTED2010)",
                    license = "ATTRIBUTION REQUIRED",
                    url = "https://github.com/tilezen/joerd/blob/master/docs/attribution.md"
                ),
                Credit(
                    title = "Offline world map",
                    detail = "Natural Earth",
                    license = "PUBLIC DOMAIN",
                    url = "https://www.naturalearthdata.com"
                )
            )
        ),
        CreditSection(
            "PHOTOS",
            listOf(
                Credit(
                    title = "Destination photos",
                    detail = "Photos provided by Pexels. Each one names its photographer on the arrival screen.",
                    license = "PEXELS LICENSE",
                    url = "https://www.pexels.com"
                )
            )
        ),
        CreditSection(
            "FLIGHT DATA",
            listOf(
                Credit(
                    title = "Routes",
                    detail = "Route data from OpenFlights.org",
                    license = "ODBL 1.0",
                    url = "https://openflights.org/data"
                ),
                Credit(
                    title = "Airports and runways",
                    detail = "OurAirports",
                    license = "PUBLIC DOMAIN",
                    url = "https://ourairports.com/data/"
                )
            )
        ),
        CreditSection(
            "3D MODELS",
            listOf(
                Credit(
                    title = "Airbus A350-1000",
                    detail = "By OUTPISTON on Sketchfab, modified for Blocktime",
                    license = "CC BY-NC-SA 4.0",
                    url = "https://sketchfab.com/3d-models/airbus-a350-1000-97577f60b81140e995d27dbb0ca36181"
                ),
                Credit(
                    title = "Boeing 787 Dreamliner cockpit",
                    detail = "By ElijahPD7000 on Sketchfab, modified for Blocktime",
                    license = "CC BY 4.0",
                    url = "https://sketchfab.com/3d-models/boeing-787-dreamliner-cockpit-db3c5818beb341cfa84013395f6c7cfb"
                )
            )
        ),
        CreditSection(
            "ARTWORK & ICONS",
            listOf(
                Credit(
                    title = "Passport world map",
                    detail = "Simple World Map by Al MacDonald, edited by Fritz Lekschas",
                    license = "CC BY-SA 3.0",
                    url = "https://github.com/flekschas/simple-world-map"
                ),
                Credit(
                    title = "Wireframe globe",
                    detail = "Sphere wireframe, Wikimedia Commons",
                    license = "GFDL · CC BY-SA",
                    url = "https://commons.wikimedia.org/wiki/File:Sphere_wireframe.svg"
                ),
                Credit(
                    title = "Airport tower icon",
                    detail = "Tabler Icons (building-airport), modified",
                    license = "MIT",
                    url = "https://github.com/tabler/tabler-icons",
                    notice = TABLER_MIT_NOTICE
                )
            )
        )
    )
}
