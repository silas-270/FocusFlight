import { CameraController } from './CameraController.js';
import { EntityBuilder } from './EntityBuilder.js';
import { FpsController, FlightPhase } from './FpsController.js';

export class CesiumEngine {
    constructor(containerId) {
        this.containerId = containerId;
        this.viewer = null;
        this.positionProperty = null;
        
        // Entities
        this.aircraftEntity = null;
        this.trackerEntity = null;
        this.corridorEntity = null; // Proxy object with .past and .future (backward compat)
        this._pastLine = null;
        this._futureLine = null;
        
        // Path pre-sampling (single source of truth for all path visualizations)
        this._pathSamples = []; // Array of { julianDate, cartesian3 }
        this._pathsVisible = true;
        this._currentMode = 'FREE';
        
        // Constants
        this.maxModelScale = 1.0;
        this.MODEL_REAL_LENGTH = 73.8;  // Real-world length of A350 in meters
        this.TARGET_PIXELS = 80;        // Desired model size on screen in pixels
        
        // State
        this.flightRectangle = null;
        this.currentMapStyle = 'HYBRID'; // Default
        this._activeImageryLayer = null;
        this._startTime = null;
        this._stopTime = null;
        
        // Subsystems
        this.cameraController = null;
        this.fpsController = null;
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    init() {
        var container = document.getElementById(this.containerId);
        if (!container) return;
        
        var w = container.clientWidth;
        var h = container.clientHeight;

        if (w === 0 || h === 0) {
            // Wait for WebView layout pass
            setTimeout(() => this.init(), 500);
            return;
        }

        try {
            this.viewer = new Cesium.Viewer(this.containerId, {
                // Start with no imagery — setMapStyle will add the correct layer
                baseLayer: false,
                terrainProvider: new Cesium.EllipsoidTerrainProvider(),
                geocoder: false,
                homeButton: false,
                sceneModePicker: false,
                navigationHelpButton: false,
                animation: false,
                timeline: false,
                fullscreenButton: false,
                vrButton: false,
                infoBox: false,
                selectionIndicator: false,
                baseLayerPicker: false,
                scene3DOnly: true,
                requestRenderMode: true
            });

            this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#001133');
            this.viewer.scene.fog.enabled = false;
            this.viewer.scene.globe.enableLighting = false;

            // Default startup view (Europe)
            this.viewer.camera.setView({
                destination: Cesium.Cartesian3.fromDegrees(10, 48, 12000000)
            });

            // Initialize Subsystems
            this.cameraController = new CameraController(this.viewer, this);
            this.fpsController = new FpsController(this.viewer);

            // Setup global clock listener (runs once)
            this.viewer.clock.onTick.addEventListener((clock) => {
                const phase = this._detectPhase(0, 0);
                this.fpsController.setPhase(phase);

                // Request render frames in requestRenderMode when simulation clock is running
                // but camera is stationary (FREE mode)
                if (clock.shouldAnimate && this._currentMode === 'FREE' && this.positionProperty) {
                    this.viewer.scene.requestRender();
                }
            });

            // Apply default map style
            this.setMapStyle(this.currentMapStyle);

            // Notify Android that engine is ready
            if (window.AndroidBridge) {
                window.AndroidBridge.onEngineInitialized();
            }

        } catch (e) {
            console.error('Initialization failed: ' + e.message);
        }
    }

    // =========================================================================
    // PUBLIC API: TELEMETRY DATA INJECTION
    // =========================================================================

    /**
     * Loads pre-computed telemetry data and starts the flight visualization.
     * Replaces the old startFlight() method. The engine no longer computes
     * geodesic paths — it only renders what it receives.
     *
     * @param {Array<{timeOffsetMs: number, longitude: number, latitude: number, altitude: number}>} telemetryPoints
     */
    loadTelemetry(telemetryPoints) {
        if (!this.viewer || !telemetryPoints || telemetryPoints.length < 2) {
            console.error('[CesiumEngine] loadTelemetry requires at least 2 points.');
            return;
        }

        // --- Clean up previous flight if any ---
        this._clearFlight();

        // Step A - Setup Timeline from telemetry timestamps
        const totalDurationMs = telemetryPoints[telemetryPoints.length - 1].timeOffsetMs;
        const startTime = Cesium.JulianDate.now();
        const stopTime = Cesium.JulianDate.addSeconds(startTime, totalDurationMs / 1000, new Cesium.JulianDate());
        
        this._startTime = startTime;
        this._stopTime = stopTime;

        this.viewer.clock.startTime = startTime;
        this.viewer.clock.stopTime = stopTime;
        this.viewer.clock.currentTime = startTime;
        this.viewer.clock.clockRange = Cesium.ClockRange.CLAMPED;
        this.viewer.clock.multiplier = 1;
        this.viewer.clock.shouldAnimate = true;

        // Step B - Build SampledPositionProperty from telemetry
        this.positionProperty = new Cesium.SampledPositionProperty();
        
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
            this.positionProperty.addSample(pointTime, position);
        }

        this.positionProperty.setInterpolationOptions({
            interpolationAlgorithm: Cesium.HermitePolynomialApproximation,
            interpolationDegree: 3
        });

        // Step B.2 - Calculate dynamic Roll (Bank Angle) based on telemetry geometry
        this.rollProperty = new Cesium.SampledProperty(Number);
        
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
            // Right turn = positive omega = positive roll (bank right)
            let rawRoll = Math.atan(speed * omega / 9.81);
            rawRoll = Math.max(-maxRoll, Math.min(maxRoll, rawRoll));
            if (speed < 10) rawRoll = 0; 

            // Calculate exact time elapsed since previous sample for accurate damping
            const stepDt = (i === 0) ? 0 : (stats[i].time - stats[i - 1].time) / 1000.0;
            const alpha = (stepDt > 0) ? 1.0 - Math.exp(-stepDt / rollTimeConstant) : 1.0;
            currentFilteredRoll = currentFilteredRoll + alpha * (rawRoll - currentFilteredRoll);

            const pointTime = Cesium.JulianDate.addSeconds(
                startTime, stats[i].time / 1000, new Cesium.JulianDate()
            );
            this.rollProperty.addSample(pointTime, currentFilteredRoll);
        }
        
        this.rollProperty.setInterpolationOptions({
            interpolationAlgorithm: Cesium.LinearApproximation
        });

        // Step B.3 - Define base and banking orientation properties
        this.baseOrientationProperty = new Cesium.VelocityOrientationProperty(this.positionProperty);
        
        this.bankingOrientationProperty = new Cesium.CallbackProperty((time, result) => {
            const baseQuat = this.baseOrientationProperty.getValue(time, result);
            if (!baseQuat) return undefined;
            
            let roll = this.rollProperty.getValue(time) || 0.0;
            if (Math.abs(roll) > 0.001) {
                // Apply roll around the local X-axis (forward vector)
                const rollQuat = Cesium.Quaternion.fromAxisAngle(Cesium.Cartesian3.UNIT_X, roll);
                Cesium.Quaternion.multiply(baseQuat, rollQuat, baseQuat);
            }
            return baseQuat;
        }, false);

        // Store bounding rectangle for FREE mode (with 0.5 degrees padding)
        this.flightRectangle = Cesium.Rectangle.fromDegrees(
            minLon - 0.5, minLat - 0.5,
            maxLon + 0.5, maxLat + 0.5
        );

        // Compute maxModelScale
        const absoluteMaxSizeMeters = 80000000.0;
        this.maxModelScale = absoluteMaxSizeMeters / this.MODEL_REAL_LENGTH;

        // Step C - Pre-sample path positions from SampledPositionProperty
        // This is the SINGLE SOURCE OF TRUTH for all path visualizations.
        this._preSamplePath();

        // Compute BoundingSphere for FREE mode perfect fit
        const cartesianPositions = this._pathSamples.map(s => s.cartesian3);
        this.flightBoundingSphere = Cesium.BoundingSphere.fromPoints(cartesianPositions);

        // Step D - Build Entities
        this._buildEntities();

        // Notify Android
        if (window.AndroidBridge && window.AndroidBridge.onFlightStarted) {
            window.AndroidBridge.onFlightStarted();
        }
        
        // Apply initial camera mode
        if (this.cameraController.getCurrentModeId()) {
            const currentMode = this.cameraController.getCurrentModeId();
            this.cameraController.activeMode = null; // force re-activation
            this.cameraController.setMode(currentMode);
        }
    }

    /**
     * Pre-samples positions from the SampledPositionProperty.
     * Uses adaptive sampling based on altitude:
     * - Low altitude (<2000m, takeoff/landing): every 2 seconds for ultra-dense curves.
     * - Mid altitude (2000m - 6000m, climb/descent): every 5 seconds.
     * - High altitude (>6000m, cruise): every 20 seconds (saves memory, but keeps Earth curvature smooth).
     * @private
     */
    _preSamplePath() {
        this._pathSamples = [];
        const durationSeconds = Cesium.JulianDate.secondsDifference(this._stopTime, this._startTime);
        
        let t = 0;
        const tempJulian = new Cesium.JulianDate();
        const cartographic = new Cesium.Cartographic();

        while (t < durationSeconds) {
            Cesium.JulianDate.addSeconds(this._startTime, t, tempJulian);
            const pos = this.positionProperty.getValue(tempJulian);
            if (!pos) {
                t += 5; // Default fallback step
                continue;
            }

            this._pathSamples.push({
                julianDate: tempJulian.clone(),
                cartesian3: pos
            });

            // Convert Cartesian3 to Geodetic Cartographic to get altitude in meters
            Cesium.Cartographic.fromCartesian(pos, undefined, cartographic);
            const altitude = cartographic.height;

            // Check if the aircraft is turning or changing altitude
            let isTurning = false;
            let isAltitudeChanging = false;
            if (t > 2 && t < durationSeconds - 2) {
                const tempPrev = new Cesium.JulianDate();
                const tempNext = new Cesium.JulianDate();
                Cesium.JulianDate.addSeconds(this._startTime, t - 2, tempPrev);
                Cesium.JulianDate.addSeconds(this._startTime, t + 2, tempNext);
                
                const posPrev = this.positionProperty.getValue(tempPrev);
                const posNext = this.positionProperty.getValue(tempNext);
                
                if (posPrev && posNext) {
                    const v1 = Cesium.Cartesian3.subtract(pos, posPrev, new Cesium.Cartesian3());
                    const v2 = Cesium.Cartesian3.subtract(posNext, pos, new Cesium.Cartesian3());
                    Cesium.Cartesian3.normalize(v1, v1);
                    Cesium.Cartesian3.normalize(v2, v2);
                    const dot = Cesium.Cartesian3.dot(v1, v2);
                    if (dot < 0.9999) {
                        isTurning = true;
                    }
                    
                    // Also check for vertical changes (climb/descent curve)
                    const cartoPrev = Cesium.Cartographic.fromCartesian(posPrev);
                    const cartoNext = Cesium.Cartographic.fromCartesian(posNext);
                    if (Math.abs(cartoNext.height - cartoPrev.height) > 1.0) { // >1m change over 4s
                        isAltitudeChanging = true;
                    }
                }
            }

            let step;
            if (isTurning || isAltitudeChanging || altitude < 2000) {
                step = 2;   // Takeoff, Landing, any Turn, or Climb/Descent curve (high density)
            } else if (altitude < 6000) {
                step = 5;   // Intermediate climb / descent
            } else {
                step = 20;  // Cruise (low density for straight segments)
            }

            t += step;
        }

        // Ensure the exact final flight position is included
        const finalPos = this.positionProperty.getValue(this._stopTime);
        if (finalPos) {
            this._pathSamples.push({
                julianDate: this._stopTime.clone(),
                cartesian3: finalPos
            });
        }
    }

    /**
     * Builds all visual entities from the prepared position data.
     * @private
     */
    _buildEntities() {
        // D.1 - Create path polylines (past = orange glow, future = white glow)
        this._createPathLines();

        // D.2 - Create invisible Tracker Entity for perfect camera logic
        this.trackerEntity = EntityBuilder.createTracker(this.viewer, this.positionProperty, this.baseOrientationProperty);

        // D.3 - Create 3D Model Entity (A350.glb)
        this.aircraftEntity = EntityBuilder.createAircraft(this.viewer, this.positionProperty, this.bankingOrientationProperty);
    }

    /**
     * Creates the two polyline entities for past and future path visualization.
     * Uses CallbackProperty so Cesium re-evaluates positions every frame
     * without needing a manual onTick listener.
     * @private
     */
    _createPathLines() {
        // We use a wider polyline to represent a "band/ribbon" look.
        // We avoid PolylineGlowMaterialProperty here because it naturally renders 
        // a solid WHITE core, which visually makes the orange line look like it's covered by white.
        this._futureLine = this.viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty(() => this._getFuturePositions(), false),
                width: 5.5,
                material: Cesium.Color.WHITE.withAlpha(0.40),
                arcType: Cesium.ArcType.NONE,
                clampToGround: false,
            }
        });

        // Use custom fade material for the past line (contrail effect)
        const FADE_MATERIAL_TYPE = 'PolylineTailFade';
        if (!Cesium.Material._materialCache.getMaterial(FADE_MATERIAL_TYPE)) {
            Cesium.Material._materialCache.addMaterial(FADE_MATERIAL_TYPE, {
                fabric: {
                    type: FADE_MATERIAL_TYPE,
                    uniforms: {
                        color: Cesium.Color.fromCssColorString('#FF8C42').withAlpha(0.90)
                    },
                    source: `
                        czm_material czm_getMaterial(czm_materialInput materialInput) {
                            czm_material material = czm_getDefaultMaterial(materialInput);
                            material.diffuse = color.rgb;
                            // materialInput.st.s goes 0.0 to 1.0 along the line.
                            // We square it for a smoother, softer alpha gradient at the tail end.
                            material.alpha = color.a * (materialInput.st.s * materialInput.st.s); 
                            return material;
                        }
                    `
                }
            });
        }

        class TailFadeMaterialProperty {
            constructor(color) {
                this._color = color;
                this._definitionChanged = new Cesium.Event();
            }
            get isConstant() { return true; }
            get definitionChanged() { return this._definitionChanged; }
            getType(time) { return FADE_MATERIAL_TYPE; }
            getValue(time, result) {
                if (!Cesium.defined(result)) result = {};
                result.color = this._color;
                return result;
            }
            equals(other) {
                return this === other || (other instanceof TailFadeMaterialProperty && Cesium.Color.equals(this._color, other._color));
            }
        }

        this._pastLine = this.viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty(() => this._getPastPositions(), false),
                width: 5.5,
                material: new TailFadeMaterialProperty(Cesium.Color.fromCssColorString('#FF8C42').withAlpha(0.90)),
                arcType: Cesium.ArcType.NONE,
                clampToGround: false,
            }
        });

        // Backward-compatible proxy so CockpitMode (which sets engine.corridorEntity.show)
        // continues to work without modifications.
        const self = this;
        this.corridorEntity = {
            past: this._pastLine,
            future: this._futureLine,
            set show(val) {
                self._pathsVisible = val;
                if (self._pastLine)   self._pastLine.show   = val;
                if (self._futureLine) self._futureLine.show = val;
            },
            get show() {
                return self._pathsVisible;
            }
        };
    }

    // =========================================================================
    // PATH CALLBACK METHODS (called by CallbackProperty every frame)
    // =========================================================================

    /**
     * Finds the index into _pathSamples that corresponds to the current clock time.
     * Linear scan is sufficient for up to 4000 samples.
     * @private
     * @returns {number}
     */
    _getCurrentSampleIndex() {
        const now = this.viewer.clock.currentTime;
        for (let i = 0; i < this._pathSamples.length - 1; i++) {
            if (Cesium.JulianDate.compare(this._pathSamples[i + 1].julianDate, now) >= 0) {
                return i;
            }
        }
        return this._pathSamples.length - 1;
    }

    /**
     * Returns Cartesian3 positions for the past (already flown) path segment.
     * Appends the exact live aircraft position to ensure seamless, real-time progression.
     * Limits the line to the last 100km to prevent clutter and enable the contrail fade.
     * @private
     * @returns {Cesium.Cartesian3[]}
     */
    _getPastPositions() {
        if (!this._pathsVisible || this._currentMode === 'COCKPIT') return [];
        if (!this._pathSamples || this._pathSamples.length < 2) return [];

        const now = this.viewer.clock.currentTime;
        const idx = this._getCurrentSampleIndex();
        
        // Start with pre-sampled points up to the current index
        let positions = this._pathSamples.slice(0, idx + 1).map(s => s.cartesian3);
        
        // Append the live position of the plane so it grows smoothly at 60 FPS
        const aircraftCurrentPos = this.positionProperty.getValue(now);
        if (aircraftCurrentPos) {
            positions.push(aircraftCurrentPos);
        }

        if (positions.length < 2) return [];

        // Reverse to start from the aircraft and go backwards
        positions.reverse();

        const MAX_DISTANCE_M = 100_000; // 100 km fading tail
        const limited = [positions[0]];
        let accumulated = 0;
        
        for (let i = 1; i < positions.length; i++) {
            const dist = Cesium.Cartesian3.distance(
                positions[i - 1],
                positions[i]
            );
            accumulated += dist;
            limited.push(positions[i]);
            
            if (accumulated >= MAX_DISTANCE_M) {
                const overshoot = accumulated - MAX_DISTANCE_M;
                const segmentLength = dist;
                if (segmentLength > 0) {
                    const fraction = (segmentLength - overshoot) / segmentLength;
                    const finalPos = Cesium.Cartesian3.lerp(
                        positions[i - 1],
                        positions[i],
                        fraction,
                        new Cesium.Cartesian3()
                    );
                    limited[limited.length - 1] = finalPos;
                }
                break;
            }
        }
        
        // Reverse back to chronological order (from tail towards aircraft)
        limited.reverse();
        
        return limited;
    }

    /**
     * Returns Cartesian3 positions for the future (upcoming) path segment.
     * Prepends the exact live aircraft position to ensure a seamless connection.
     * In TRACKING mode, limits to ~20km ahead of the aircraft.
     * In FREE mode, returns the entire remaining route.
     * @private
     * @returns {Cesium.Cartesian3[]}
     */
    _getFuturePositions() {
        if (!this._pathsVisible || this._currentMode === 'COCKPIT') return [];
        if (!this._pathSamples || this._pathSamples.length < 2) return [];

        const now = this.viewer.clock.currentTime;
        const idx = this._getCurrentSampleIndex();
        
        const aircraftCurrentPos = this.positionProperty.getValue(now);
        let positions;
        
        if (aircraftCurrentPos) {
            // Start exactly at the aircraft's current position and append all upcoming pre-sampled points
            const remaining = this._pathSamples.slice(idx + 1);
            positions = [aircraftCurrentPos, ...remaining.map(s => s.cartesian3)];
        } else {
            // Fallback if clock is out of bounds
            const remaining = this._pathSamples.slice(idx);
            positions = remaining.map(s => s.cartesian3);
        }

        if (positions.length < 2) return [];

        if (this._currentMode === 'TRACKING') {
            // Only show the next ~20km of the future route
            const MAX_DISTANCE_M = 20_000;
            const limited = [positions[0]];
            let accumulated = 0;
            for (let i = 1; i < positions.length; i++) {
                const dist = Cesium.Cartesian3.distance(
                    positions[i - 1],
                    positions[i]
                );
                accumulated += dist;
                limited.push(positions[i]);
                if (accumulated >= MAX_DISTANCE_M) {
                    const overshoot = accumulated - MAX_DISTANCE_M;
                    const segmentLength = dist;
                    if (segmentLength > 0) {
                        const fraction = (segmentLength - overshoot) / segmentLength;
                        const finalPos = Cesium.Cartesian3.lerp(
                            positions[i - 1],
                            positions[i],
                            fraction,
                            new Cesium.Cartesian3()
                        );
                        limited[limited.length - 1] = finalPos;
                    }
                    break;
                }
            }
            return limited;
        }

        // FREE: entire future route
        return positions;
    }

    /**
     * Removes all entities from a previous flight.
     * @private
     */
    _clearFlight() {
        // Clean up path lines
        if (this._pastLine)   { this.viewer.entities.remove(this._pastLine);   this._pastLine   = null; }
        if (this._futureLine) { this.viewer.entities.remove(this._futureLine); this._futureLine = null; }
        this._pathSamples = [];
        this.corridorEntity = null;

        if (this.trackerEntity) {
            this.viewer.entities.remove(this.trackerEntity);
            this.trackerEntity = null;
        }
        if (this.aircraftEntity) {
            this.viewer.entities.remove(this.aircraftEntity);
            this.aircraftEntity = null;
        }
        this.positionProperty = null;
        this.flightRectangle = null;
        this._startTime = null;
        this._stopTime = null;
    }

    // =========================================================================
    // PUBLIC API: CAMERA MODE
    // =========================================================================

    /**
     * @param {'FREE' | 'TRACKING' | 'COCKPIT'} modeId
     */
    setMode(modeId) {
        this._currentMode = modeId;

        // Lines hidden in COCKPIT, visible otherwise.
        // The CallbackProperties also check _currentMode to return [] in COCKPIT,
        // but .show controls whether the entity is rendered at all.
        const showLines = modeId !== 'COCKPIT';
        this._pathsVisible = showLines;
        if (this._pastLine)   this._pastLine.show   = showLines;
        if (this._futureLine) this._futureLine.show = showLines;

        if (this.cameraController) {
            this.cameraController.setMode(modeId);
        }
    }

    // =========================================================================
    // PUBLIC API: MAP STYLE
    // =========================================================================

    /**
     * Switches the globe imagery layer.
     * @param {'OSM' | 'DARK' | 'NIGHTLIGHTS' | 'HYBRID'} styleId
     */
    setMapStyle(styleId) {
        if (!this.viewer) return;
        
        const layers = this.viewer.scene.imageryLayers;

        // Remove the current active imagery layer (if any)
        if (this._activeImageryLayer) {
            layers.remove(this._activeImageryLayer, true);
            this._activeImageryLayer = null;
        }

        this.currentMapStyle = styleId;

        switch (styleId) {
            case 'DARK':
                // No imagery layer — just the dark baseColor of the globe
                // This is the maximum performance mode
                this.viewer.scene.globe.enableLighting = false;
                break;

            case 'OSM':
                this._activeImageryLayer = layers.addImageryProvider(
                    new Cesium.OpenStreetMapImageryProvider({
                        url: 'https://tile.openstreetmap.org/'
                    })
                );
                break;

            case 'NIGHTLIGHTS':
                // NASA Black Marble (Earth at Night)
                Cesium.IonImageryProvider.fromAssetId(3812).then((provider) => {
                    if (this.currentMapStyle !== 'NIGHTLIGHTS') return;
                    this._activeImageryLayer = layers.addImageryProvider(provider);
                }).catch((err) => {
                    console.warn('[CesiumEngine] Failed to load NIGHTLIGHTS imagery:', err);
                });
                this.viewer.scene.globe.enableLighting = true;
                break;

            case 'HYBRID':
            default:
                // ArcGIS World Imagery (Satellite)
                Cesium.ArcGisMapServerImageryProvider.fromUrl(
                    'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer',
                    { enablePickFeatures: false }
                ).then((provider) => {
                    // Guard: user may have switched style while this was loading
                    if (this.currentMapStyle !== 'HYBRID') return;
                    this._activeImageryLayer = layers.addImageryProvider(provider);
                }).catch((err) => {
                    console.warn('[CesiumEngine] Failed to load HYBRID imagery:', err);
                });
                break;
        }

        // Notify Android
        if (window.AndroidBridge && window.AndroidBridge.onMapStyleChanged) {
            window.AndroidBridge.onMapStyleChanged(styleId);
        }
    }

    // =========================================================================
    // PUBLIC API: PLAYBACK CONTROLS
    // =========================================================================

    /**
     * Starts or resumes the flight animation.
     */
    play() {
        if (!this.viewer) return;
        this.viewer.clock.shouldAnimate = true;
    }

    /**
     * Pauses the flight animation. The airplane freezes in place.
     */
    pause() {
        if (!this.viewer) return;
        this.viewer.clock.shouldAnimate = false;
    }

    /**
     * Sets the playback speed multiplier.
     * @param {number} mult - e.g. 1 for realtime, 2 for 2x speed, 0.5 for half speed
     */
    setSpeedMultiplier(mult) {
        if (!this.viewer) return;
        this.viewer.clock.multiplier = mult;
    }

    /**
     * Seeks to a specific point in the flight.
     * @param {number} fraction - 0.0 (start) to 1.0 (end)
     */
    seekTo(fraction) {
        if (!this.viewer) return;
        fraction = Math.max(0, Math.min(1, fraction));
        
        const start = this.viewer.clock.startTime;
        const stop = this.viewer.clock.stopTime;
        const totalSeconds = Cesium.JulianDate.secondsDifference(stop, start);
        
        const targetTime = Cesium.JulianDate.addSeconds(
            start, totalSeconds * fraction, new Cesium.JulianDate()
        );
        this.viewer.clock.currentTime = targetTime;
    }

    // =========================================================================
    // FPS PHASE DETECTION STUB
    // =========================================================================

    /**
     * Stub method to detect the current flight phase based on telemetry.
     * @param {number} altitude 
     * @param {number} verticalSpeed 
     * @returns {string} One of FlightPhase constants
     */
    _detectPhase(altitude, verticalSpeed) {
        // TODO: Implement actual phase logic based on telemetry.
        return FlightPhase.CRUISE;
    }
}
