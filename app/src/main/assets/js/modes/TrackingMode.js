import { CameraMode } from './CameraMode.js';
import { MathUtils } from '../MathUtils.js';

// Static scratch variables to eliminate per-frame GC allocations
const scratchRotationMatrix = new Cesium.Matrix3();
const scratchWorldOffset = new Cesium.Cartesian3();
const scratchTargetCameraPos = new Cesium.Cartesian3();
const scratchTargetCameraDir = new Cesium.Cartesian3();
const scratchTargetAirplaneUp = new Cesium.Cartesian3();
const scratchRight = new Cesium.Cartesian3();
const scratchTargetCameraUp = new Cesium.Cartesian3();
const scratchCurrentPos = new Cesium.Cartesian3();
const scratchCurrentDir = new Cesium.Cartesian3();
const scratchCurrentUp = new Cesium.Cartesian3();
const scratchCurrentRight = new Cesium.Cartesian3();

export class TrackingMode extends CameraMode {
    activate() {
        this.viewer.camera.cancelFlight();
        this.viewer.trackedEntity = undefined;

        this._startTransition();
    }

    reset() {
        this.viewer.camera.cancelFlight();
        this.viewer.trackedEntity = undefined;
        this._startTransition();
    }

    _startTransition() {
        if (!this.engine.trackerEntity || !this.engine.positionProperty) return;

        // --- TRACKING CAMERA CONFIGURATION ---
        const INITIAL_DISTANCE = 500.0;
        const HEADING_DEG = -45.0; // Corrected!
        const PITCH_DEG = -22.0;   // Corrected!

        const headingRad = Cesium.Math.toRadians(HEADING_DEG);
        const pitchRad = Cesium.Math.toRadians(PITCH_DEG);
        
        // Lokaler Offset des Trackers (X=Vorne, Y=Links, Z=Oben)
        const xyDistance = INITIAL_DISTANCE * Math.cos(Math.abs(pitchRad));
        const xOffset = -xyDistance * Math.cos(headingRad);
        const yOffset = -xyDistance * Math.sin(headingRad);
        const zOffset = INITIAL_DISTANCE * Math.sin(Math.abs(pitchRad));
        const localOffset = new Cesium.Cartesian3(xOffset, yOffset, zOffset);
        
        // Diesen Offset schon mal für Cesium setzen, damit er nach der Animation greift
        this.engine.trackerEntity.viewFrom = localOffset;

        // Verhindere alte Ghost-Listener, falls reset() gespammt wird
        if (this._tickListener) {
            this.viewer.clock.onTick.removeEventListener(this._tickListener);
            this._tickListener = null;
        }

        this.startPos = this.viewer.camera.positionWC.clone();
        this.startDir = this.viewer.camera.directionWC.clone();
        this.startUp  = this.viewer.camera.upWC.clone();
        
        this.transitionVirtualElapsed = 0.0;
        this.lastClockTime = this.viewer.clock.currentTime;
        this.lastRealTime = performance.now();
        this.transitionDurationSeconds = 1.5;

        const easeInOutCubic = (t) => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

        const transitionListener = (clock) => {
            this.viewer.camera.cancelFlight(); // Verhindere andere Animationen während des Übergangs

            // --- HYBRID TIMER ---
            // Wir nutzen die Simulations-Zeit für absolute Synchronität (verhindert Ruckeln).
            // Wenn die Simulations-Uhr aber pausiert (z.B. Ziel erreicht), nutzen wir als 
            // Fallback die echte Zeit, damit die Animation nicht einfriert.
            const nowReal = performance.now();
            const deltaReal = (nowReal - this.lastRealTime) / 1000.0;
            this.lastRealTime = nowReal;

            const deltaClock = Cesium.JulianDate.secondsDifference(clock.currentTime, this.lastClockTime);
            this.lastClockTime = clock.currentTime;

            if (deltaClock > 0) {
                this.transitionVirtualElapsed += deltaClock;
            } else {
                this.transitionVirtualElapsed += deltaReal;
            }

            let rawT = this.transitionVirtualElapsed / this.transitionDurationSeconds;
            
            if (rawT < 1.0) {
                // Berechne die absolute Zielposition des Trackers in DIESEM Frame
                let currentAirplanePos = this.engine.positionProperty.getValue(clock.currentTime);
                let currentAirplaneOri = this.engine.trackerEntity.orientation.getValue(clock.currentTime);

                if (!currentAirplanePos || !currentAirplaneOri) return;

                const rotationMatrix = Cesium.Matrix3.fromQuaternion(currentAirplaneOri, scratchRotationMatrix);
                const worldOffset = Cesium.Matrix3.multiplyByVector(rotationMatrix, localOffset, scratchWorldOffset);
                const targetCameraPos = Cesium.Cartesian3.add(currentAirplanePos, worldOffset, scratchTargetCameraPos);
                
                // Die Kamera muss auf das Flugzeug gucken
                let targetCameraDir = Cesium.Cartesian3.subtract(currentAirplanePos, targetCameraPos, scratchTargetCameraDir);
                Cesium.Cartesian3.normalize(targetCameraDir, targetCameraDir);
                
                // Kamera 'Up' Vektor aus der Flugzeug-Rotation berechnen
                const targetAirplaneUp = Cesium.Matrix3.getColumn(rotationMatrix, 2, scratchTargetAirplaneUp);
                const right = Cesium.Cartesian3.cross(targetCameraDir, targetAirplaneUp, scratchRight);
                Cesium.Cartesian3.normalize(right, right);
                const targetCameraUp = Cesium.Cartesian3.cross(right, targetCameraDir, scratchTargetCameraUp);
                Cesium.Cartesian3.normalize(targetCameraUp, targetCameraUp);

                // Smooth Interpolation anwenden
                const t = easeInOutCubic(rawT);
                const currentPos = Cesium.Cartesian3.lerp(this.startPos, targetCameraPos, t, scratchCurrentPos);
                
                let currentDir = Cesium.Cartesian3.lerp(this.startDir, targetCameraDir, t, scratchCurrentDir);
                Cesium.Cartesian3.normalize(currentDir, currentDir);
                
                let currentUp = Cesium.Cartesian3.lerp(this.startUp, targetCameraUp, t, scratchCurrentUp);
                Cesium.Cartesian3.normalize(currentUp, currentUp);

                const currentRight = Cesium.Cartesian3.cross(currentDir, currentUp, scratchCurrentRight);
                Cesium.Cartesian3.normalize(currentRight, currentRight);
                currentUp = Cesium.Cartesian3.cross(currentRight, currentDir, scratchCurrentUp);
                Cesium.Cartesian3.normalize(currentUp, currentUp);

                this.viewer.camera.setView({
                    destination: currentPos,
                    orientation: { direction: currentDir, up: currentUp }
                });
            } else {
                // Perfekt angekommen. Übergabe an Cesium Tracking Engine.
                if (this.engine.cameraController.getCurrentModeId() === 'TRACKING') {
                    this.viewer.trackedEntity = this.engine.trackerEntity;
                }
                
                // Entferne genau DIESEN spezifischen Listener
                this.viewer.clock.onTick.removeEventListener(transitionListener);
                if (this._tickListener === transitionListener) {
                    this._tickListener = null;
                }
            }
        };
        
        this._tickListener = transitionListener;
        this.viewer.clock.onTick.addEventListener(this._tickListener);
    }
    
    deactivate() {
        super.deactivate();
        this.viewer.trackedEntity = undefined;
    }
}
