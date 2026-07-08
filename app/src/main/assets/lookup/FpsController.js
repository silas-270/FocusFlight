export const FlightPhase = {
    GROUND: 'GROUND',
    TAKEOFF: 'TAKEOFF',
    CLIMB: 'CLIMB',
    CRUISE: 'CRUISE',
    DESCENT: 'DESCENT',
    APPROACH: 'APPROACH'
};

const ABSOLUTE_MIN_FPS = 3;
const ABSOLUTE_MAX_FPS = 30;
const INTERACTION_FPS = 30;

// Base FPS for each phase based on visual change rate and altitude
const PHASE_FPS_TARGETS = {
    [FlightPhase.GROUND]: 3,    // Aircraft is static or taxiing very slowly.
    [FlightPhase.TAKEOFF]: 30,  // Fast movement, very close to ground. High visual change.
    [FlightPhase.CLIMB]: 15,    // Altitude increasing, ground texture moving slower. Moderate visual change.
    [FlightPhase.CRUISE]: 5,    // High altitude, very slow apparent movement over hours. Saves massive battery.
    [FlightPhase.DESCENT]: 15,  // Altitude decreasing, ground texture moving faster. Moderate visual change.
    [FlightPhase.APPROACH]: 30  // Final approach, close to ground, high visual change.
};

export class FpsController {
    constructor(viewer) {
        this.viewer = viewer;
        this.enabled = true;
        
        this.currentPhase = FlightPhase.GROUND;
        this.currentMode = 'AUTO'; // 'AUTO', 'PERFORMANCE', 'QUALITY'
        
        this.isInteracting = false;
        this.interactionCooldownTimeout = null;
        
        this.transitionInterval = null;
        
        this._setupInteractionListeners();
        
        // Initial setup
        this._updateTargetFps(false); // No smooth transition on boot
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Updates the current flight phase and adjusts FPS accordingly.
     * @param {string} phase - One of FlightPhase constants
     */
    setPhase(phase) {
        if (!FlightPhase[phase] || this.currentPhase === phase) return;
        this.currentPhase = phase;
        this._updateTargetFps(true);
    }

    /**
     * Enables or disables the FPS controller.
     * @param {boolean} enabled 
     */
    setEnabled(enabled) {
        this.enabled = enabled;
        this._updateTargetFps(false);
    }

    /**
     * Overrides the automatic FPS logic with fixed profiles.
     * @param {'AUTO' | 'PERFORMANCE' | 'QUALITY'} mode 
     */
    setMode(mode) {
        if (this.currentMode === mode) return;
        this.currentMode = mode;
        this._updateTargetFps(true);
    }

    /**
     * @returns {number} The currently active targetFrameRate in the Cesium viewer.
     */
    getCurrentFps() {
        return this.viewer.targetFrameRate;
    }

    // =========================================================================
    // INTERNAL LOGIC
    // =========================================================================

    _setupInteractionListeners() {
        // Detect when user starts moving the camera
        this.viewer.camera.moveStart.addEventListener(() => {
            this.isInteracting = true;
            
            // Clear any pending cooldowns
            if (this.interactionCooldownTimeout) {
                clearTimeout(this.interactionCooldownTimeout);
                this.interactionCooldownTimeout = null;
            }
            
            // Instantly apply interaction FPS (no smooth transition)
            this._updateTargetFps(false);
        });

        // Detect when user stops moving the camera
        this.viewer.camera.moveEnd.addEventListener(() => {
            // Apply 2.5s cooldown before dropping FPS again
            this.interactionCooldownTimeout = setTimeout(() => {
                this.isInteracting = false;
                this.interactionCooldownTimeout = null;
                this._updateTargetFps(true);
            }, 2500);
        });
    }

    _updateTargetFps(smooth = true) {
        if (!this.enabled) return;

        let targetFps;

        if (this.currentMode === 'PERFORMANCE') {
            targetFps = ABSOLUTE_MIN_FPS;
        } else if (this.currentMode === 'QUALITY') {
            targetFps = ABSOLUTE_MAX_FPS;
        } else {
            // AUTO Mode
            if (this.isInteracting) {
                targetFps = INTERACTION_FPS;
            } else {
                targetFps = PHASE_FPS_TARGETS[this.currentPhase] || ABSOLUTE_MIN_FPS;
            }
        }

        // Clamp to absolute bounds
        targetFps = Math.max(ABSOLUTE_MIN_FPS, Math.min(targetFps, ABSOLUTE_MAX_FPS));

        if (this.viewer.targetFrameRate === targetFps) return;

        if (smooth) {
            // When user starts interacting, jump instantly to avoid perceived lag.
            // When jumping down, smooth it out.
            if (this.isInteracting && this.currentMode === 'AUTO') {
                this._setImmediateFps(targetFps);
            } else {
                this._smoothTransition(targetFps, 1000); // 1 second smooth transition
            }
        } else {
            this._setImmediateFps(targetFps);
        }
    }

    _setImmediateFps(fps) {
        if (this.transitionInterval) {
            clearInterval(this.transitionInterval);
            this.transitionInterval = null;
        }
        this.viewer.targetFrameRate = Math.round(fps);
    }

    /**
     * Smoothly transitions the viewer's targetFrameRate over durationMs.
     */
    _smoothTransition(targetFps, durationMs) {
        if (this.transitionInterval) {
            clearInterval(this.transitionInterval);
        }

        const startFps = this.viewer.targetFrameRate;
        const fpsDiff = targetFps - startFps;
        
        if (fpsDiff === 0) return;

        const startTime = performance.now();

        this.transitionInterval = setInterval(() => {
            const elapsed = performance.now() - startTime;
            let fraction = elapsed / durationMs;
            
            if (fraction >= 1.0) {
                fraction = 1.0;
                clearInterval(this.transitionInterval);
                this.transitionInterval = null;
            }

            // EaseInOut interpolation
            const easeFraction = fraction < 0.5 ? 2 * fraction * fraction : -1 + (4 - 2 * fraction) * fraction;
            const currentSmoothFps = startFps + (fpsDiff * easeFraction);
            
            this.viewer.targetFrameRate = Math.round(currentSmoothFps);
            
            // Force Cesium scene request render to ensure frame rate updates apply during static scenes
            this.viewer.scene.requestRender();
        }, 33); // Run interval approx at 30fps
    }
}
