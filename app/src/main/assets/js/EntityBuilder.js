import { MathUtils } from './MathUtils.js';

export class EntityBuilder {
    /**
     * Creates two route corridors (past and future) that meet dynamically at the airplane.
     */
    static createCorridors(viewer, degreesArray, positionProperty, getScaleMult, getMode) {
        const fullPositions = Cesium.Cartesian3.fromDegreesArrayHeights(degreesArray);
        const numPositions = fullPositions.length;

        const widthProp = new Cesium.CallbackProperty(() => {
            const currentMode = getMode ? getMode() : null;
            const baseWidth = (currentMode === 'COCKPIT') ? 2 : 5; // pixels now
            return baseWidth;
        }, false);

        const pastCorridor = viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty((time) => {
                    const currentPos = positionProperty.getValue(time);
                    if (!currentPos) return [fullPositions[0], fullPositions[1]];

                    const start = viewer.clock.startTime;
                    const stop = viewer.clock.stopTime;
                    const totalDuration = Cesium.JulianDate.secondsDifference(stop, start);
                    if (totalDuration <= 0) return [fullPositions[0], fullPositions[1]];

                    const elapsed = Cesium.JulianDate.secondsDifference(time, start);
                    let fraction = Math.max(0, Math.min(1, elapsed / totalDuration));

                    let exactIndex = fraction * (numPositions - 1);
                    let lastIndex = Math.floor(exactIndex);

                    let arr = [];
                    let dist = 0;
                    const mode = getMode ? getMode() : null;
                    const maxDist = (mode === 'FREE') ? Infinity : 50000; // 50km in Tracking/Cockpit
                    
                    arr.push(currentPos);
                    for (let i = lastIndex; i >= 0; i--) {
                        const d = Cesium.Cartesian3.distance(arr[arr.length - 1], fullPositions[i]);
                        if (d > 1.0) {
                            arr.push(fullPositions[i]);
                            dist += d;
                            if (dist > maxDist) break;
                        }
                    }
                    if (arr.length < 2) arr.push(fullPositions[0]);
                    arr.reverse();
                    return arr;
                }, false),
                width: widthProp,
                material: new Cesium.PolylineGlowMaterialProperty({
                    glowPower: 0.2,
                    color: Cesium.Color.ORANGE.withAlpha(0.8)
                })
            }
        });

        const futureCorridor = viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty((time) => {
                    const currentPos = positionProperty.getValue(time);
                    if (!currentPos) return [fullPositions[numPositions - 2], fullPositions[numPositions - 1]];

                    const start = viewer.clock.startTime;
                    const stop = viewer.clock.stopTime;
                    const totalDuration = Cesium.JulianDate.secondsDifference(stop, start);
                    if (totalDuration <= 0) return [fullPositions[numPositions - 2], fullPositions[numPositions - 1]];

                    const elapsed = Cesium.JulianDate.secondsDifference(time, start);
                    let fraction = Math.max(0, Math.min(1, elapsed / totalDuration));

                    let exactIndex = fraction * (numPositions - 1);
                    let nextIndex = Math.ceil(exactIndex);

                    if (nextIndex >= numPositions) nextIndex = numPositions - 1;

                    let arr = [currentPos];
                    let dist = 0;
                    const mode = getMode ? getMode() : null;
                    const maxDist = (mode === 'FREE') ? Infinity : 100000; // 100km in Tracking/Cockpit
                    
                    for (let i = nextIndex; i < numPositions; i++) {
                        const d = Cesium.Cartesian3.distance(arr[arr.length - 1], fullPositions[i]);
                        if (d > 1.0) {
                            arr.push(fullPositions[i]);
                            dist += d;
                            if (dist > maxDist) break;
                        }
                    }
                    if (arr.length < 2) arr.unshift(fullPositions[numPositions - 2]);
                    return arr;
                }, false),
                width: widthProp,
                material: new Cesium.PolylineDashMaterialProperty({
                    color: Cesium.Color.WHITE.withAlpha(0.6),
                    dashLength: 20.0
                })
            }
        });

        // Return a proxy object so CockpitMode's show=false setter affects both
        return {
            past: pastCorridor,
            future: futureCorridor,
            set show(val) {
                this.past.show = val;
                this.future.show = val;
            }
        };
    }

    /**
     * Creates an invisible tracker entity used purely for anchoring the camera in TRACKING mode.
     * Decouples the camera offset from the visual scaling of the aircraft.
     */
    static createTracker(viewer, positionProperty, baseOrientation) {
        return viewer.entities.add({
            position: positionProperty,
            orientation: baseOrientation,
            point: {
                pixelSize: 1,
                color: Cesium.Color.TRANSPARENT
            }
        });
    }

    /**
     * Creates the main 3D Aircraft Entity (A350).
     */
    static createAircraft(viewer, positionProperty, bankingOrientation) {
        return viewer.entities.add({
            position: positionProperty,
            orientation: bankingOrientation,
            model: {
                uri: 'models/A350.glb',
                scale: 100.0, // Correction factor: 3D model is exported in cm (0.738m). 100x makes it real 73.8m.
                // dynamically assigned in setMode
                minimumPixelSize: 96,
                maximumScale: 20000.0,
                runAnimations: false // Disable embedded glTF animations (prevents the model from bobbing up/down on its own)
            },
            path: {
                show: false
            }
        });
    }
}
