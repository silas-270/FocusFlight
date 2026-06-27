export class CameraMode {
    constructor(viewer, engine) {
        this.viewer = viewer;
        this.engine = engine; // Reference back to the orchestrator for shared state
        this._tickListener = null;
    }

    /**
     * Called when the mode is activated.
     */
    activate() {
        // To be implemented by subclasses
    }

    /**
     * Called when the mode is deactivated. Cleans up any resources or listeners.
     */
    deactivate() {
        if (this._tickListener) {
            this.viewer.clock.onTick.removeEventListener(this._tickListener);
            this._tickListener = null;
        }
    }

    /**
     * Called when the active mode is selected again.
     */
    reset() {
        // To be implemented by subclasses
    }
}
