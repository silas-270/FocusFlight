import { FreeMode } from './modes/FreeMode.js';
import { TrackingMode } from './modes/TrackingMode.js';
import { CockpitMode } from './modes/CockpitMode.js';

export class CameraController {
    constructor(viewer, engine) {
        this.viewer = viewer;
        this.engine = engine;
        
        this.modes = {
            'FREE': new FreeMode(viewer, engine),
            'TRACKING': new TrackingMode(viewer, engine),
            'COCKPIT': new CockpitMode(viewer, engine)
        };
        
        this.currentModeId = null;
        this.activeMode = null;
        
        // Listen to manual tracking break (user drags camera in tracking mode)
        this.viewer.trackedEntityChanged.addEventListener((entity) => {
            if (entity === undefined && this.currentModeId === 'TRACKING') {
                this.setMode('FREE');
            }
        });
    }

    getCurrentModeId() {
        return this.currentModeId;
    }

    setMode(modeId) {
        if (!this.modes[modeId]) {
            console.warn('Unknown mode:', modeId);
            return;
        }

        if (this.currentModeId === modeId) {
            // Bereits in diesem Modus. Klick dient als "Reset"-Befehl für den aktuellen View.
            if (this.activeMode) {
                this.activeMode.reset();
            }
            return;
        }

        // Deactivate old mode
        if (this.activeMode) {
            this.activeMode.deactivate();
        }

        // Clear native tracked entity and cancel flights to prevent camera fighting
        this.viewer.camera.cancelFlight();
        this.viewer.trackedEntity = undefined;

        this.currentModeId = modeId;
        this.activeMode = this.modes[modeId];

        // Notify Android of mode change if it originated from JS (e.g., untrack)
        if (window.AndroidBridge && window.AndroidBridge.onCameraModeChanged) {
            window.AndroidBridge.onCameraModeChanged(modeId);
        }

        // Apply shared scale limits for the 3D model
        if (this.engine.aircraftEntity && this.engine.aircraftEntity.model) {
            if (modeId === 'FREE') {
                this.engine.aircraftEntity.model.minimumPixelSize = this.engine.TARGET_PIXELS;
            } else {
                this.engine.aircraftEntity.model.minimumPixelSize = 96;
            }
            // maximumScale is shared identically across all modes to prevent size exploding
            this.engine.aircraftEntity.model.maximumScale = this.engine.maxModelScale;
        }

        // Activate new mode
        this.activeMode.activate();
    }
}
