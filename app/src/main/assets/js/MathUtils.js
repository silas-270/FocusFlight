export class MathUtils {
    /**
     * Computes the exact scale multiplier that Cesium applies internally to the airplane model
     * via minimumPixelSize and maximumScale. Used to scale the corridor identically.
     */
    static computeScaleMultiplier(viewer, positionProperty, targetPixels, modelRealLength, maxModelScale, currentMode) {
        try {
            if (!positionProperty || !viewer) return 1.0;
            const position = positionProperty.getValue(viewer.clock.currentTime);
            if (!position) return 1.0;

            const camera = viewer.camera;
            const distance = Cesium.Cartesian3.distance(camera.positionWC, position);
            if (distance <= 0) return 1.0;

            const frustum = camera.frustum;
            let fovY = frustum.fovy;
            if (!fovY || fovY <= 0) {
                const fov = frustum.fov;
                if (!fov || fov <= 0) return 1.0;
                const canvas = viewer.canvas;
                if (canvas.clientWidth > canvas.clientHeight) {
                    const aspectRatio = canvas.clientWidth / canvas.clientHeight;
                    fovY = 2.0 * Math.atan(Math.tan(fov / 2.0) / aspectRatio);
                } else {
                    fovY = fov;
                }
            }

            const canvasHeightCSS = viewer.canvas.clientHeight;
            if (!canvasHeightCSS || canvasHeightCSS <= 0) return 1.0;

            // Perspective projection: meters visible per CSS pixel at this distance
            const metersPerCssPixel = (2.0 * distance * Math.tan(fovY / 2.0)) / canvasHeightCSS;
            
            // The required multiplier for the real-size model to reach target pixels on screen
            const requiredScale = (targetPixels * metersPerCssPixel) / modelRealLength;
            
            // Clamp it using the exact same limits as the model
            return Math.max(1.0, Math.min(requiredScale, maxModelScale));
        } catch (e) {
            return 1.0;
        }
    }

    /**
     * Computes heading, pitch, and roll (as radians, degrees, and quaternion) for a given time on the route.
     * Implements a fallback to look backwards if the flight has ended and no future position is available.
     */
    static computeOrientation(positionProperty, julianDate) {
        if (!positionProperty) return null;
        const position = positionProperty.getValue(julianDate);
        if (!position) return null;

        // Position 2 seconds in the future for direction vector
        const futureTime = Cesium.JulianDate.addSeconds(
            julianDate, 2.0, new Cesium.JulianDate()
        );
        let futurePosition = positionProperty.getValue(futureTime);
        let currentPosForVector = position;

        if (!futurePosition) {
            // End of flight reached, no future positions in interpolation.
            // Look 2 seconds in the past instead to determine final approach heading.
            const pastTime = Cesium.JulianDate.addSeconds(
                julianDate, -2.0, new Cesium.JulianDate()
            );
            const pastPosition = positionProperty.getValue(pastTime);
            if (!pastPosition) return null;

            futurePosition = position;
            currentPosForVector = pastPosition;
        }

        // Local East-North-Up coordinate system at current position
        const enuTransform = Cesium.Transforms.eastNorthUpToFixedFrame(position);
        const inverseEnu = Cesium.Matrix4.inverseTransformation(
            enuTransform, new Cesium.Matrix4()
        );

        // Transform world velocity vector into local ENU coordinates
        // ENU: X = East, Y = North, Z = Up
        const worldVelocity = Cesium.Cartesian3.subtract(
            futurePosition, currentPosForVector, new Cesium.Cartesian3()
        );
        const localVelocity = Cesium.Matrix4.multiplyByPointAsVector(
            inverseEnu, worldVelocity, new Cesium.Cartesian3()
        );

        // Heading: Angle from North, clockwise
        const headingRad = Math.atan2(localVelocity.x, localVelocity.y);

        // Pitch: Climb or sink angle relative to horizontal
        const horizontalMag = Math.sqrt(
            localVelocity.x ** 2 + localVelocity.y ** 2
        );
        const pitchRad = Math.atan2(localVelocity.z, horizontalMag);

        // Roll: Bank angle - 0 for MVP
        const rollRad = 0.0;

        const hpr = new Cesium.HeadingPitchRoll(headingRad, pitchRad, rollRad);

        return {
            heading: Cesium.Math.toDegrees(headingRad),
            pitch:   Cesium.Math.toDegrees(pitchRad),
            roll:    0,
            headingRad, pitchRad, rollRad,
            quaternion: Cesium.Transforms.headingPitchRollQuaternion(position, hpr)
        };
    }
}
