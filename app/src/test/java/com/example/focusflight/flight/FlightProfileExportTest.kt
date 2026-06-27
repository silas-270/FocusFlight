package com.example.focusflight.flight

import com.google.gson.Gson
import org.junit.Test
import java.io.File

class FlightProfileExportTest {

    @Test
    fun exportToJson() {
        val routes = listOf(
            Triple("STR_FRA", Pair(9.222, 48.689), Pair(8.571, 50.033)),
            Triple("STR_JFK", Pair(9.222, 48.689), Pair(-73.778, 40.641))
        )
        val gson = Gson()
        routes.forEach { (name, dep, arr) ->
            val points = FlightTelemetryGenerator.generate(
                dep.first, dep.second,
                arr.first, arr.second,
                if (name == "STR_FRA") 1_800_000L else 32_400_000L
            )
            // Save in root directory of the project for easy access
            val file = File("flight_$name.json")
            file.writeText(gson.toJson(points))
            println("$name: ${points.size} Punkte → ${file.absolutePath}")
        }
    }
}
