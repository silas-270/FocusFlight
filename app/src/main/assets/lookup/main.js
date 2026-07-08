import { CesiumEngine } from './CesiumEngine.js';

// Global engine instance
const engine = new CesiumEngine('cesiumContainer');

// =========================================================================
// GLOBAL API — Callable from Android WebView via evaluateJavascript()
// =========================================================================

/**
 * Loads telemetry data and starts the flight visualization.
 * @param {Array<{timeOffsetMs: number, longitude: number, latitude: number, altitude: number}>} points
 */
window.loadTelemetry = (points) => { /* engine.loadTelemetry(points); */ };

/**
 * Sets the camera perspective mode.
 * @param {'FREE' | 'TRACKING' | 'COCKPIT'} modeId
 */
window.setMode = (modeId) => { /* engine.setMode(modeId); */ };

/**
 * Switches the globe imagery layer.
 * @param {'OSM' | 'DARK' | 'NIGHTLIGHTS' | 'HYBRID'} styleId
 */
window.setMapStyle = (styleId) => { /* engine.setMapStyle(styleId); */ };

/**
 * Switches the sunset mode.
 * @param {'CUSTOM' | 'DEFAULT'} modeId
 */
window.setSunsetMode = (modeId) => { /* engine.setSunsetMode(modeId); */ };

/**
 * Starts or resumes the flight animation.
 */
window.play = () => { /* engine.play(); */ };

/**
 * Pauses the flight animation.
 */
window.pause = () => { /* engine.pause(); */ };

/**
 * Sets the playback speed multiplier (e.g. 1, 2, 5).
 * @param {number} mult
 */
window.setSpeedMultiplier = (mult) => { /* engine.setSpeedMultiplier(mult); */ };

/**
 * Seeks to a fraction of the flight (0.0 = start, 1.0 = end).
 * @param {number} fraction
 */
window.seekTo = (fraction) => { /* engine.seekTo(fraction); */ };

/**
 * Sets the FPS controller mode.
 * @param {'AUTO' | 'PERFORMANCE' | 'QUALITY'} mode
 */
window.setFpsMode = (mode) => {
    /*
    if (engine.fpsController) {
        engine.fpsController.setMode(mode);
    }
    */
};

// Keep legacy reference for backward compatibility
window.engine = engine;

// Start initialization
setTimeout(() => engine.init(), 500);
