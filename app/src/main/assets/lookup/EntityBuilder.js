

export class EntityBuilder {

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
                runAnimations: false, // Disable embedded glTF animations (prevents the model from bobbing up/down on its own)
                customShader: new Cesium.CustomShader({
                    translucencyMode: Cesium.CustomShaderTranslucencyMode.OPAQUE
                })
            },
            path: {
                show: false
            }
        });
    }
}
