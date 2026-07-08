export class LabelManager {
    constructor(engine) {
        this.engine = engine;
    }

    clearMinimalistLabels() {
        for (const ds of this.engine._minimalistDataSources) {
            this.engine.viewer.dataSources.remove(ds, true);
        }
        this.engine._minimalistDataSources = [];
    }

    applyMinimalistLabels(isDark) {
        const labelColor = Cesium.Color.LIGHTGRAY;

        const cities = [
            // Europe
            { name: "Berlin", lat: 52.5200, lon: 13.4050 }, { name: "Hamburg", lat: 53.5511, lon: 9.9937 },
            { name: "Munich", lat: 48.1351, lon: 11.5820 }, { name: "Cologne", lat: 50.9375, lon: 6.9603 },
            { name: "Frankfurt", lat: 50.1109, lon: 8.6821 }, { name: "Stuttgart", lat: 48.7758, lon: 9.1829 },
            { name: "Düsseldorf", lat: 51.2277, lon: 6.7735 }, { name: "Leipzig", lat: 51.3397, lon: 12.3731 },
            { name: "London", lat: 51.5074, lon: -0.1278 }, { name: "Paris", lat: 48.8566, lon: 2.3522 },
            { name: "Rome", lat: 41.9028, lon: 12.4964 }, { name: "Madrid", lat: 40.4168, lon: -3.7038 },
            { name: "Barcelona", lat: 41.3851, lon: 2.1734 }, { name: "Amsterdam", lat: 52.3676, lon: 4.9041 },
            { name: "Vienna", lat: 48.2082, lon: 16.3738 }, { name: "Zurich", lat: 47.3769, lon: 8.5417 },
            { name: "Geneva", lat: 46.2044, lon: 6.1432 }, { name: "Brussels", lat: 50.8503, lon: 4.3517 },
            { name: "Copenhagen", lat: 55.6761, lon: 12.5683 }, { name: "Stockholm", lat: 59.3293, lon: 18.0686 },
            { name: "Oslo", lat: 59.9139, lon: 10.7522 }, { name: "Helsinki", lat: 60.1695, lon: 24.9354 },
            { name: "Warsaw", lat: 52.2297, lon: 21.0122 }, { name: "Prague", lat: 50.0755, lon: 14.4378 },
            { name: "Budapest", lat: 47.4979, lon: 19.0402 }, { name: "Athens", lat: 37.9838, lon: 23.7275 },
            { name: "Lisbon", lat: 38.7223, lon: -9.1393 }, { name: "Dublin", lat: 53.3498, lon: -6.2603 },
            { name: "Istanbul", lat: 41.0082, lon: 28.9784 }, { name: "Moscow", lat: 55.7558, lon: 37.6173 },
            // North America
            { name: "New York", lat: 40.7128, lon: -74.0060 }, { name: "Los Angeles", lat: 34.0522, lon: -118.2437 },
            { name: "Chicago", lat: 41.8781, lon: -87.6298 }, { name: "Houston", lat: 29.7604, lon: -95.3698 },
            { name: "Phoenix", lat: 33.4484, lon: -112.0740 }, { name: "Philadelphia", lat: 39.9526, lon: -75.1652 },
            { name: "San Francisco", lat: 37.7749, lon: -122.4194 }, { name: "Seattle", lat: 47.6062, lon: -122.3321 },
            { name: "Miami", lat: 25.7617, lon: -80.1918 }, { name: "Atlanta", lat: 33.7490, lon: -84.3880 },
            { name: "Toronto", lat: 43.6510, lon: -79.3470 }, { name: "Montreal", lat: 45.5017, lon: -73.5673 },
            { name: "Vancouver", lat: 49.2827, lon: -123.1207 }, { name: "Mexico City", lat: 19.4326, lon: -99.1332 },
            // South America
            { name: "São Paulo", lat: -23.5505, lon: -46.6333 }, { name: "Buenos Aires", lat: -34.6037, lon: -58.3816 },
            { name: "Rio de Janeiro", lat: -22.9068, lon: -43.1729 }, { name: "Bogotá", lat: 4.7110, lon: -74.0721 },
            { name: "Lima", lat: -12.0464, lon: -77.0428 }, { name: "Santiago", lat: -33.4489, lon: -70.6693 },
            // Asia
            { name: "Tokyo", lat: 35.6762, lon: 139.6503 }, { name: "Delhi", lat: 28.6139, lon: 77.2090 },
            { name: "Shanghai", lat: 31.2304, lon: 121.4737 }, { name: "Beijing", lat: 39.9042, lon: 116.4074 },
            { name: "Mumbai", lat: 19.0760, lon: 72.8777 }, { name: "Osaka", lat: 34.6937, lon: 135.5023 },
            { name: "Seoul", lat: 37.5665, lon: 126.9780 }, { name: "Bangkok", lat: 13.7563, lon: 100.5018 },
            { name: "Singapore", lat: 1.3521, lon: 103.8198 }, { name: "Hong Kong", lat: 22.3193, lon: 114.1694 },
            { name: "Dubai", lat: 25.2048, lon: 55.2708 }, { name: "Riyadh", lat: 24.7136, lon: 46.6753 },
            { name: "Kuala Lumpur", lat: 3.1390, lon: 101.6869 }, { name: "Jakarta", lat: -6.2088, lon: 106.8456 },
            // Africa & Oceania
            { name: "Cairo", lat: 30.0444, lon: 31.2357 }, { name: "Johannesburg", lat: -26.2041, lon: 28.0473 },
            { name: "Lagos", lat: 6.5244, lon: 3.3792 }, { name: "Nairobi", lat: -1.2921, lon: 36.8219 },
            { name: "Sydney", lat: -33.8688, lon: 151.2093 }, { name: "Melbourne", lat: -37.8136, lon: 144.9631 },
            { name: "Auckland", lat: -36.8485, lon: 174.7633 }
        ];

        const citiesDataSource = new Cesium.CustomDataSource('cities');

        const displayCondition = new Cesium.DistanceDisplayCondition(0.0, 10000000.0);

        cities.forEach(city => {
            const cityCartesian = Cesium.Cartesian3.fromDegrees(city.lon, city.lat);

            const showProperty = new Cesium.CallbackProperty((time) => {
                if (!this.engine.aircraftEntity || !this.engine.positionProperty) return false;
                const aircraftPos = this.engine.positionProperty.getValue(time);
                if (!aircraftPos) return false;

                const distance = Cesium.Cartesian3.distance(cityCartesian, aircraftPos);
                return distance < 2500000; 
            }, false);

            citiesDataSource.entities.add({
                position: cityCartesian,
                show: showProperty,
                point: {
                    pixelSize: 4,
                    color: labelColor,
                    distanceDisplayCondition: displayCondition
                },
                label: {
                    text: city.name,
                    font: '14pt sans-serif',
                    fillColor: labelColor,
                    style: Cesium.LabelStyle.FILL,
                    verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
                    pixelOffset: new Cesium.Cartesian2(0, -10),
                    distanceDisplayCondition: displayCondition
                }
            });
        });

        this.engine.viewer.dataSources.add(citiesDataSource);
        this.engine._minimalistDataSources.push(citiesDataSource);
    }
}
