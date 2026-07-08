// Imports removed for clean slate (referencing from temporary folder if needed)

export class CesiumEngine {
    constructor(containerId) {
        this.containerId = containerId;
        this.viewer = null;
        this.positionProperty = null;
        
        // Entities
        this.aircraftEntity = null;
        this.trackerEntity = null;
        this.corridorEntity = null; // Proxy object with .past and .future
        this._pastLine = null;
        this._futureLine = null;
        
        // Path pre-sampling
        this._pathSamples = []; 
        this._pathsVisible = true;
        this._currentMode = 'FREE';
        
        // Constants
        this.maxModelScale = 1.0;
        this.MODEL_REAL_LENGTH = 73.8;  
        this.TARGET_PIXELS = 80;        
        
        // State
        this.flightRectangle = null;
        this.currentMapStyle = 'SATELLITE'; 
        this._activeImageryLayer = null;
        this._minimalistDataSources = [];
        this._startTime = null;
        this._stopTime = null;
        this._atmosphereTickListener = null;
        this._isDarkMode = false;
        
        // Subsystems
        this.cameraController = null;
        this.fpsController = null;
        
        // Managers (set to null, code removed to temp folder)
        this.telemetryManager = null;
        this.pathManager = null;
        this.atmosphereManager = null;
        this.labelManager = null;
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
            setTimeout(() => this.init(), 500);
            return;
        }

        try {
            Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJmZjI4Y2Y2NS0zNzE0LTQ1MmQtYjBmNC1kNTgyYzZkN2I4MWEiLCJpZCI6NDQ5ODU3LCJpc3MiOiJodHRwczovL2FwaS5jZXNpdW0uY29tIiwiYXVkIjoidW5kZWZpbmVkX2RlZmF1bHQiLCJpYXQiOjE3ODI2MDAxMDF9.VQgeqc-D0V6O8WfiH2LmXmhIDIxlt-hsLOJxkSEoRZU';

            this.viewer = new Cesium.Viewer(this.containerId, {
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

            this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#1E90FF'); // Blue sphere
            this.viewer.scene.fog.enabled = false;
            this.viewer.scene.globe.enableLighting = false;

            if (this.viewer.scene.skyBox) this.viewer.scene.skyBox.show = false;
            if (this.viewer.scene.sun) this.viewer.scene.sun.show = false;
            if (this.viewer.scene.moon) this.viewer.scene.moon.show = false;
            if (this.viewer.scene.skyAtmosphere) this.viewer.scene.skyAtmosphere.show = false;
            this.viewer.scene.globe.showGroundAtmosphere = false;

            this.viewer.camera.setView({
                destination: Cesium.Cartesian3.fromDegrees(10, 48, 12000000)
            });

            /*
            this.cameraController = new CameraController(this.viewer, this);
            this.fpsController = new FpsController(this.viewer);

            this.viewer.clock.onTick.addEventListener((clock) => {
                const phase = this._detectPhase(0, 0);
                this.fpsController.setPhase(phase);

                if (clock.shouldAnimate && this._currentMode === 'FREE' && this.positionProperty) {
                    this.viewer.scene.requestRender();
                }
            });

            this.setMapStyle(this.currentMapStyle);
            */

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

    loadTelemetry(telemetryPoints) {
        this.telemetryManager.loadTelemetry(telemetryPoints);
    }

    // =========================================================================
    // PUBLIC API: CAMERA MODE
    // =========================================================================

    setMode(modeId) {
        this._currentMode = modeId;

        const showLines = modeId !== 'COCKPIT';
        this._pathsVisible = showLines;
        if (this._pastLine) {
            this._pastLine.show = showLines;
            this._pastLine.polyline.material = (modeId === 'TRACKING') ? this.pathManager.tailFadeMaterial : this.pathManager.solidPastMaterial;
        }
        if (this._futureLine) {
            this._futureLine.show = showLines;
            this._futureLine.polyline.material = (modeId === 'TRACKING') ? this.pathManager.headFadeMaterial : this.pathManager.solidFutureMaterial;
        }

        if (this.cameraController) {
            this.cameraController.setMode(modeId);
        }
    }

    // =========================================================================
    // PUBLIC API: MAP STYLE
    // =========================================================================

    setMapStyle(styleId) {
        if (!this.viewer) return;
        
        const layers = this.viewer.scene.imageryLayers;

        if (this._activeImageryLayer) {
            layers.remove(this._activeImageryLayer, true);
            this._activeImageryLayer = null;
        }

        this.labelManager.clearMinimalistLabels();

        this.currentMapStyle = styleId;

        const isDark = (styleId === 'NIGHTLIGHTS' || styleId === 'MINIMALIST_DARK');
        this._isDarkMode = isDark;

        switch (styleId) {
            case 'MINIMALIST':
                this._activeImageryLayer = layers.addImageryProvider(
                    new Cesium.UrlTemplateImageryProvider({
                        url: 'https://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png',
                        subdomains: ['a', 'b', 'c', 'd'],
                        credit: 'Map tiles by Carto, under CC BY 3.0. Data by OpenStreetMap, under ODbL.'
                    })
                );
                this.viewer.scene.globe.enableLighting = false;
                this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#F5F5F5');
                this.labelManager.applyMinimalistLabels(false);
                this.atmosphereManager.clearAtmosphere();
                break;

            case 'MINIMALIST_DARK':
                this._activeImageryLayer = layers.addImageryProvider(
                    new Cesium.UrlTemplateImageryProvider({
                        url: 'https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}.png',
                        subdomains: ['a', 'b', 'c', 'd'],
                        credit: 'Map tiles by Carto, under CC BY 3.0. Data by OpenStreetMap, under ODbL.'
                    })
                );
                this.viewer.scene.globe.enableLighting = false;
                this.viewer.scene.globe.dynamicAtmosphereLighting = true;
                this.viewer.scene.globe.dynamicAtmosphereLightingFromSun = false;
                if (this.viewer.scene.atmosphere) {
                    this.viewer.scene.atmosphere.dynamicLighting = Cesium.DynamicAtmosphereLightingType.SCENE_LIGHT;
                }
                this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#0A1628');
                this.labelManager.applyMinimalistLabels(true);
                this.atmosphereManager.applyAtmosphere();
                break;

            case 'NIGHTLIGHTS':
                Cesium.IonImageryProvider.fromAssetId(3812, {
                    accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJmZjI4Y2Y2NS0zNzE0LTQ1MmQtYjBmNC1kNTgyYzZkN2I4MWEiLCJpZCI6NDQ5ODU3LCJpc3MiOiJodHRwczovL2FwaS5jZXNpdW0uY29tIiwiYXVkIjoidW5kZWZpbmVkX2RlZmF1bHQiLCJpYXQiOjE3ODI2MDAxMDF9.VQgeqc-D0V6O8WfiH2LmXmhIDIxlt-hsLOJxkSEoRZU'
                }).then((provider) => {
                    if (this.currentMapStyle !== 'NIGHTLIGHTS') return;
                    this._activeImageryLayer = layers.addImageryProvider(provider);
                }).catch((err) => {
                    let errMsg = err;
                    if (err instanceof Error) errMsg = err.message;
                    else if (typeof err === 'object') {
                        try { errMsg = JSON.stringify(err); } catch(e) {}
                    }
                    console.warn('[CesiumEngine] Failed to load NIGHTLIGHTS imagery:', errMsg);
                });
                this.viewer.scene.globe.enableLighting = false;
                this.viewer.scene.globe.dynamicAtmosphereLighting = true;
                this.viewer.scene.globe.dynamicAtmosphereLightingFromSun = false;
                if (this.viewer.scene.atmosphere) {
                    this.viewer.scene.atmosphere.dynamicLighting = Cesium.DynamicAtmosphereLightingType.SCENE_LIGHT;
                }
                this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#001133');
                this.atmosphereManager.applyAtmosphere();
                break;

            case 'SATELLITE':
            case 'HYBRID': 
            default:
                Cesium.ArcGisMapServerImageryProvider.fromUrl(
                    'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer',
                    { enablePickFeatures: false }
                ).then((provider) => {
                    if (this.currentMapStyle !== 'SATELLITE' && this.currentMapStyle !== 'HYBRID') return;
                    this._activeImageryLayer = layers.addImageryProvider(provider);
                }).catch((err) => {
                    console.warn('[CesiumEngine] Failed to load SATELLITE imagery:', err);
                });
                this.viewer.scene.globe.enableLighting = false;
                this.viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#001133');
                this.atmosphereManager.clearAtmosphere();
                break;
        }

        if (window.AndroidBridge && window.AndroidBridge.onMapStyleChanged) {
            window.AndroidBridge.onMapStyleChanged(styleId);
        }
    }

    // =========================================================================
    // PUBLIC API: PLAYBACK CONTROLS
    // =========================================================================

    play() {
        if (!this.viewer) return;
        this.viewer.clock.shouldAnimate = true;
    }

    pause() {
        if (!this.viewer) return;
        this.viewer.clock.shouldAnimate = false;
    }

    setSpeedMultiplier(mult) {
        if (!this.viewer) return;
        this.viewer.clock.multiplier = mult;
    }

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
    
    setSunsetMode(mode) {
        if (this.atmosphereManager) {
            this.atmosphereManager.setSunsetMode(mode);
        }
    }

    // =========================================================================
    // HELPER METHODS CALLED BY MANAGERS
    // =========================================================================
    _buildEntities() {
        /*
        this.pathManager.createPathLines();
        this.trackerEntity = EntityBuilder.createTracker(this.viewer, this.positionProperty, this.baseOrientationProperty);
        this.aircraftEntity = EntityBuilder.createAircraft(this.viewer, this.positionProperty, this.bankingOrientationProperty);
        */
    }

    _detectPhase(altitude, verticalSpeed) {
        return 'CRUISE';
    }
}
