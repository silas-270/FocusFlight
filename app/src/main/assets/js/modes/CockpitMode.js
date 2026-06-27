import { CameraMode } from './CameraMode.js';
import { MathUtils } from '../MathUtils.js';

// Static scratch variables to eliminate per-frame GC allocations
const scratchRotationMatrix = new Cesium.Matrix3();
const scratchForwardAxis = new Cesium.Cartesian3();
const scratchUpAxis = new Cesium.Cartesian3();
const scratchForwardVector = new Cesium.Cartesian3();
const scratchUpVector = new Cesium.Cartesian3();
const scratchTargetCockpitPosition = new Cesium.Cartesian3();
const scratchRightAxis = new Cesium.Cartesian3();
const scratchPitchDownQuat = new Cesium.Quaternion();
const scratchPitchDownMat = new Cesium.Matrix3();
const scratchTargetCameraDirection = new Cesium.Cartesian3();
const scratchTargetCameraUp = new Cesium.Cartesian3();
const scratchCurrentPos = new Cesium.Cartesian3();
const scratchCurrentDir = new Cesium.Cartesian3();
const scratchCurrentUp = new Cesium.Cartesian3();
const scratchCurrentRight = new Cesium.Cartesian3();

export class CockpitMode extends CameraMode {
    activate() {
        // Disable screen inputs so the camera is fully passive
        this.viewer.scene.screenSpaceCameraController.enableInputs = false;
        
        // Disable native tracking and flying just once upon activation
        this.viewer.camera.cancelFlight();
        this.viewer.trackedEntity = undefined;

        // --- Custom Animation Transition Setup ---
        // Store the exact camera position and orientation before switching to cockpit
        this.startPos = this.viewer.camera.positionWC.clone();
        this.startDir = this.viewer.camera.directionWC.clone();
        this.startUp  = this.viewer.camera.upWC.clone();
        
        this.transitionVirtualElapsed = 0.0;
        this.lastClockTime = this.viewer.clock.currentTime;
        this.lastRealTime = performance.now();
        this.transitionDurationSeconds = 1.5; // Dauer der Kamerafahrt ins Cockpit

        // Helper Funktion für weiche Interpolation (Ease-In-Out Cubic)
        const easeInOutCubic = (t) => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

        this._tickListener = (clock) => {
            if (!this.engine.positionProperty || !this.engine.trackerEntity) return;
            
            const position = this.engine.positionProperty.getValue(clock.currentTime);
            if (!position) return;

            // Target Orientation vom Flugzeug holen (inkl. Roll-Neigung für den Cockpit-Modus)
            const quaternion = this.engine.bankingOrientationProperty.getValue(clock.currentTime);
            if (!quaternion) return;

            // Target Vektoren berechnen
            const rotationMatrix = Cesium.Matrix3.fromQuaternion(quaternion, scratchRotationMatrix);
            const forwardAxis = Cesium.Matrix3.getColumn(rotationMatrix, 0, scratchForwardAxis);
            const upAxis      = Cesium.Matrix3.getColumn(rotationMatrix, 2, scratchUpAxis);
            
            const FORWARD_OFFSET = 33.0;
            const UP_OFFSET      = 5.0;
            
            const forwardVector = Cesium.Cartesian3.multiplyByScalar(forwardAxis, FORWARD_OFFSET, scratchForwardVector);
            const upVector      = Cesium.Cartesian3.multiplyByScalar(upAxis, UP_OFFSET, scratchUpVector);
            
            let targetCockpitPosition = Cesium.Cartesian3.add(position, forwardVector, scratchTargetCockpitPosition);
            targetCockpitPosition     = Cesium.Cartesian3.add(targetCockpitPosition, upVector, scratchTargetCockpitPosition);

            // Target Pitch (3 Grad nach unten)
            const rightAxis = Cesium.Cartesian3.cross(forwardAxis, upAxis, scratchRightAxis);
            Cesium.Cartesian3.normalize(rightAxis, rightAxis);

            const pitchDownQuat = Cesium.Quaternion.fromAxisAngle(rightAxis, Cesium.Math.toRadians(-3), scratchPitchDownQuat);
            const pitchDownMat = Cesium.Matrix3.fromQuaternion(pitchDownQuat, scratchPitchDownMat);

            const targetCameraDirection = Cesium.Matrix3.multiplyByVector(pitchDownMat, forwardAxis, scratchTargetCameraDirection);
            const targetCameraUp = Cesium.Matrix3.multiplyByVector(pitchDownMat, upAxis, scratchTargetCameraUp);

            // --- HYBRID TIMER ---
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
                // Mitten in der Animation!
                const t = easeInOutCubic(rawT);

                // Position interpolieren (LERP)
                const currentPos = Cesium.Cartesian3.lerp(this.startPos, targetCockpitPosition, t, scratchCurrentPos);

                // Wir wandeln Direction/Up kurz in ein Quaternion um, um sie mit SLERP (sphärisch) weich zu rotieren
                // Note: Da Cesium keine direkte Diretion/Up SLERP anbietet, mischen wir die Vektoren
                // mit normalem LERP und normalisieren sie wieder. Da wir nur in 1.5s fliegen, ist das optisch einwandfrei.
                let currentDir = Cesium.Cartesian3.lerp(this.startDir, targetCameraDirection, t, scratchCurrentDir);
                Cesium.Cartesian3.normalize(currentDir, currentDir);
                
                let currentUp = Cesium.Cartesian3.lerp(this.startUp, targetCameraUp, t, scratchCurrentUp);
                Cesium.Cartesian3.normalize(currentUp, currentUp);

                // Orthogonalisierung, damit Kamera nicht verzerrt
                const currentRight = Cesium.Cartesian3.cross(currentDir, currentUp, scratchCurrentRight);
                Cesium.Cartesian3.normalize(currentRight, currentRight);
                currentUp = Cesium.Cartesian3.cross(currentRight, currentDir, scratchCurrentUp);
                Cesium.Cartesian3.normalize(currentUp, currentUp);

                this.viewer.camera.setView({
                    destination: currentPos,
                    orientation: {
                        direction: currentDir,
                        up: currentUp
                    }
                });
            } else {
                // Animation fertig, 100% Lock an das Ziel
                this.viewer.camera.setView({
                    destination: targetCockpitPosition,
                    orientation: { direction: targetCameraDirection, up: targetCameraUp }
                });

                // Performance Optimization: Hide airplane and line when fully inside the cockpit
                if (this.engine.aircraftEntity) this.engine.aircraftEntity.show = false;
                if (this.engine.corridorEntity) this.engine.corridorEntity.show = false;
            }
        };
        this.viewer.clock.onTick.addEventListener(this._tickListener);
    }

    reset() {
        // Cockpit ist fixiert, kein manueller Zoom-In nötig.
    }

    deactivate() {
        super.deactivate(); // Entfernt den _tickListener
        this.viewer.scene.screenSpaceCameraController.enableInputs = true;

        // Restore visibility instantly when leaving Cockpit mode
        if (this.engine.aircraftEntity) this.engine.aircraftEntity.show = true;
        if (this.engine.corridorEntity) this.engine.corridorEntity.show = true;
    }
}
