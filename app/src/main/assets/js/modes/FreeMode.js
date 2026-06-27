import { CameraMode } from './CameraMode.js';

export class FreeMode extends CameraMode {
    activate() {
        if (this.engine.flightRectangle) {
            this.viewer.camera.flyTo({
                destination: this.engine.flightRectangle,
                duration: 1.5
            });
        }
        
        // No _tickListener needed here. Cesium handles animation frame requests automatically
        // when entities (like our position property) change over time.
    }

    reset() {
        if (this.engine.flightRectangle) {
            this.viewer.camera.flyTo({
                destination: this.engine.flightRectangle,
                duration: 1.5
            });
        }
    }
}
