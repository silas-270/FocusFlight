export class TelemetryManager {
    constructor(engine) {
        this.engine = engine;
    }

    loadTelemetry(telemetryPoints) {
        if (!this.engine.viewer || !telemetryPoints || telemetryPoints.length < 2) {
            console.error('[CesiumEngine] loadTelemetry requires at least 2 points.');
            return;
        }

        // --- Clean up previous flight if any ---
        this.clearFlight();

        // Step A - Setup Timeline from telemetry timestamps
        const totalDurationMs = telemetryPoints[telemetryPoints.length - 1].timeOffsetMs;
        const startTime = Cesium.JulianDate.now();
        const stopTime = Cesium.JulianDate.addSeconds(startTime, totalDurationMs / 1000, new Cesium.JulianDate());
        
        this.engine._startTime = startTime;
        this.engine._stopTime = stopTime;

        this.engine.viewer.clock.startTime = startTime;
        this.engine.viewer.clock.stopTime = stopTime;
        this.engine.viewer.clock.currentTime = startTime;
        this.engine.viewer.clock.clockRange = Cesium.ClockRange.CLAMPED;
        this.engine.viewer.clock.multiplier = 1;
        this.engine.viewer.clock.shouldAnimate = true;

        // Step B - Build SampledPositionProperty from telemetry
        this.engine.positionProperty = new Cesium.SampledPositionProperty();
        
        let minLon = Infinity, minLat = Infinity, maxLon = -Infinity, maxLat = -Infinity;

        for (const point of telemetryPoints) {
            // Bounding box for FREE mode
            if (point.longitude < minLon) minLon = point.longitude;
            if (point.longitude > maxLon) maxLon = point.longitude;
            if (point.latitude < minLat) minLat = point.latitude;
            if (point.latitude > maxLat) maxLat = point.latitude;

            const position = Cesium.Cartesian3.fromDegrees(
                point.longitude, 
                point.latitude, 
                point.altitude
            );
            const pointTime = Cesium.JulianDate.addSeconds(
                startTime, point.timeOffsetMs / 1000, new Cesium.JulianDate()
            );
            this.engine.positionProperty.addSample(pointTime, position);
        }

        this.engine.positionProperty.setInterpolationOptions({
            interpolationAlgorithm: Cesium.HermitePolynomialApproximation,
            interpolationDegree: 3
        });

        // Step B.2 - Calculate dynamic Roll (Bank Angle) based on telemetry geometry
        this.engine.rollProperty = new Cesium.SampledProperty(Number);
        
        const getHeading = (p1, p2) => {
            const lat1 = Cesium.Math.toRadians(p1.latitude);
            const lat2 = Cesium.Math.toRadians(p2.latitude);
            const lon1 = Cesium.Math.toRadians(p1.longitude);
            const lon2 = Cesium.Math.toRadians(p2.longitude);
            const y = Math.sin(lon2 - lon1) * Math.cos(lat2);
            const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
            return Math.atan2(y, x);
        };
        
        const stats = [];
        for (let i = 0; i < telemetryPoints.length; i++) {
            let prev = i > 0 ? telemetryPoints[i - 1] : telemetryPoints[i];
            let next = i < telemetryPoints.length - 1 ? telemetryPoints[i + 1] : telemetryPoints[i];
            
            const dt = (next.timeOffsetMs - prev.timeOffsetMs) / 1000.0;
            const heading = getHeading(prev, next);
            
            const posPrev = Cesium.Cartesian3.fromDegrees(prev.longitude, prev.latitude, prev.altitude);
            const posNext = Cesium.Cartesian3.fromDegrees(next.longitude, next.latitude, next.altitude);
            const dist = Cesium.Cartesian3.distance(posPrev, posNext);
            
            const speed = (dt > 0) ? dist / dt : 0;
            stats.push({ time: telemetryPoints[i].timeOffsetMs, heading: heading, speed: speed });
        }
        
        let currentFilteredRoll = 0;
        const rollTimeConstant = 3.0; // Seconds for inertia/lag
        
        for (let i = 0; i < stats.length; i++) {
            let prev = i > 0 ? stats[i - 1] : stats[i];
            let next = i < stats.length - 1 ? stats[i + 1] : stats[i];
            
            const dt = (next.time - prev.time) / 1000.0;
            let dHeading = next.heading - prev.heading;
            
            while (dHeading > Math.PI) dHeading -= 2 * Math.PI;
            while (dHeading < -Math.PI) dHeading += 2 * Math.PI;
            
            const omega = (dt > 0) ? dHeading / dt : 0;
            const speed = stats[i].speed;
            
            const maxRoll = 0.61; // ~35 degrees
            let rawRoll = Math.atan(speed * omega / 9.81);
            rawRoll = Math.max(-maxRoll, Math.min(maxRoll, rawRoll));
            if (speed < 10) rawRoll = 0; 

            const stepDt = (i === 0) ? 0 : (stats[i].time - stats[i - 1].time) / 1000.0;
            const alpha = (stepDt > 0) ? 1.0 - Math.exp(-stepDt / rollTimeConstant) : 1.0;
            currentFilteredRoll = currentFilteredRoll + alpha * (rawRoll - currentFilteredRoll);

            const pointTime = Cesium.JulianDate.addSeconds(
                startTime, stats[i].time / 1000, new Cesium.JulianDate()
            );
            this.engine.rollProperty.addSample(pointTime, currentFilteredRoll);
        }
        
        this.engine.rollProperty.setInterpolationOptions({
            interpolationAlgorithm: Cesium.LinearApproximation
        });

        // Step B.3 - Define base and banking orientation properties
        this.engine.baseOrientationProperty = new Cesium.VelocityOrientationProperty(this.engine.positionProperty);
        
        this.engine.bankingOrientationProperty = new Cesium.CallbackProperty((time, result) => {
            const baseQuat = this.engine.baseOrientationProperty.getValue(time, result);
            if (!baseQuat) return undefined;
            
            let roll = this.engine.rollProperty.getValue(time) || 0.0;
            if (Math.abs(roll) > 0.001) {
                const rollQuat = Cesium.Quaternion.fromAxisAngle(Cesium.Cartesian3.UNIT_X, roll);
                Cesium.Quaternion.multiply(baseQuat, rollQuat, baseQuat);
            }
            return baseQuat;
        }, false);

        this.engine.flightRectangle = Cesium.Rectangle.fromDegrees(
            minLon - 0.5, minLat - 0.5,
            maxLon + 0.5, maxLat + 0.5
        );

        const absoluteMaxSizeMeters = 80000000.0;
        this.engine.maxModelScale = absoluteMaxSizeMeters / this.engine.MODEL_REAL_LENGTH;

        // Step C - Pre-sample path positions from SampledPositionProperty
        if (this.engine.pathManager) {
            this.engine.pathManager.preSamplePath();
        }

        if (this.engine._pathSamples && this.engine._pathSamples.length > 0) {
            const cartesianPositions = this.engine._pathSamples.map(s => s.cartesian3);
            this.engine.flightBoundingSphere = Cesium.BoundingSphere.fromPoints(cartesianPositions);
        }

        // Step D - Build Entities
        if (this.engine._buildEntities) {
            this.engine._buildEntities();
        }

        // Notify Android
        if (window.AndroidBridge && window.AndroidBridge.onFlightStarted) {
            window.AndroidBridge.onFlightStarted();
        }
        
        if (this.engine.cameraController && this.engine.cameraController.getCurrentModeId()) {
            const currentMode = this.engine.cameraController.getCurrentModeId();
            this.engine.cameraController.activeMode = null; 
            this.engine.cameraController.setMode(currentMode);
        }
    }

    clearFlight() {
        if (this.engine.pathManager) {
            this.engine.pathManager.clearFlight();
        }

        if (this.engine.trackerEntity) {
            this.engine.viewer.entities.remove(this.engine.trackerEntity);
            this.engine.trackerEntity = null;
        }
        if (this.engine.aircraftEntity) {
            this.engine.viewer.entities.remove(this.engine.aircraftEntity);
            this.engine.aircraftEntity = null;
        }
        this.engine.positionProperty = null;
        this.engine.rollProperty = null;
        this.engine.baseOrientationProperty = null;
        this.engine.bankingOrientationProperty = null;
        this.engine.flightRectangle = null;
        this.engine.flightBoundingSphere = null;
        this.engine._startTime = null;
        this.engine._stopTime = null;
    }
}
