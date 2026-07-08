export class PathManager {
    constructor(engine) {
        this.engine = engine;
        this.headFadeMaterial = null;
        this.tailFadeMaterial = null;
        this.solidFutureMaterial = null;
        this.solidPastMaterial = null;
    }

    preSamplePath() {
        this.engine._pathSamples = [];
        if (!this.engine._startTime || !this.engine._stopTime || !this.engine.positionProperty) {
            return;
        }
        const durationSeconds = Cesium.JulianDate.secondsDifference(this.engine._stopTime, this.engine._startTime);
        
        let t = 0;
        const tempJulian = new Cesium.JulianDate();
        const cartographic = new Cesium.Cartographic();

        while (t < durationSeconds) {
            Cesium.JulianDate.addSeconds(this.engine._startTime, t, tempJulian);
            const pos = this.engine.positionProperty.getValue(tempJulian);
            if (!pos) {
                t += 5; // Default fallback step
                continue;
            }

            this.engine._pathSamples.push({
                julianDate: tempJulian.clone(),
                cartesian3: pos
            });

            // Convert Cartesian3 to Geodetic Cartographic to get altitude in meters
            Cesium.Cartographic.fromCartesian(pos, undefined, cartographic);
            const altitude = cartographic.height;

            // Check if the aircraft is turning or changing altitude
            let isTurning = false;
            let isAltitudeChanging = false;
            if (t > 2 && t < durationSeconds - 2) {
                const tempPrev = new Cesium.JulianDate();
                const tempNext = new Cesium.JulianDate();
                Cesium.JulianDate.addSeconds(this.engine._startTime, t - 2, tempPrev);
                Cesium.JulianDate.addSeconds(this.engine._startTime, t + 2, tempNext);
                
                const posPrev = this.engine.positionProperty.getValue(tempPrev);
                const posNext = this.engine.positionProperty.getValue(tempNext);
                
                if (posPrev && posNext) {
                    const v1 = Cesium.Cartesian3.subtract(pos, posPrev, new Cesium.Cartesian3());
                    const v2 = Cesium.Cartesian3.subtract(posNext, pos, new Cesium.Cartesian3());
                    Cesium.Cartesian3.normalize(v1, v1);
                    Cesium.Cartesian3.normalize(v2, v2);
                    const dot = Cesium.Cartesian3.dot(v1, v2);
                    if (dot < 0.9999) {
                        isTurning = true;
                    }
                    
                    // Also check for vertical changes (climb/descent curve)
                    const cartoPrev = Cesium.Cartographic.fromCartesian(posPrev);
                    const cartoNext = Cesium.Cartographic.fromCartesian(posNext);
                    if (Math.abs(cartoNext.height - cartoPrev.height) > 1.0) { // >1m change over 4s
                        isAltitudeChanging = true;
                    }
                }
            }

            let step;
            if (isTurning || isAltitudeChanging || altitude < 2000) {
                step = 2;   // Takeoff, Landing, any Turn, or Climb/Descent curve (high density)
            } else if (altitude < 6000) {
                step = 5;   // Intermediate climb / descent
            } else {
                step = 20;  // Cruise (low density for straight segments)
            }

            t += step;
        }

        // Ensure the exact final flight position is included
        const finalPos = this.engine.positionProperty.getValue(this.engine._stopTime);
        if (finalPos) {
            this.engine._pathSamples.push({
                julianDate: this.engine._stopTime.clone(),
                cartesian3: finalPos
            });
        }
    }

    createPathLines() {
        const HEAD_FADE_MATERIAL_TYPE = 'PolylineHeadFade';
        if (!Cesium.Material._materialCache.getMaterial(HEAD_FADE_MATERIAL_TYPE)) {
            Cesium.Material._materialCache.addMaterial(HEAD_FADE_MATERIAL_TYPE, {
                fabric: {
                    type: HEAD_FADE_MATERIAL_TYPE,
                    uniforms: {
                        color: Cesium.Color.WHITE.withAlpha(0.40)
                    },
                    source: `
                        czm_material czm_getMaterial(czm_materialInput materialInput) {
                            czm_material material = czm_getDefaultMaterial(materialInput);
                            material.diffuse = color.rgb;
                            // materialInput.st.s goes 0.0 (aircraft) to 1.0 (future end)
                            float t = 1.0 - materialInput.st.s;
                            material.alpha = color.a * (t * t); 
                            return material;
                        }
                    `
                }
            });
        }

        class HeadFadeMaterialProperty {
            constructor(color) {
                this._color = color;
                this._definitionChanged = new Cesium.Event();
            }
            get isConstant() { return true; }
            get definitionChanged() { return this._definitionChanged; }
            getType(time) { return HEAD_FADE_MATERIAL_TYPE; }
            getValue(time, result) {
                if (!Cesium.defined(result)) result = {};
                result.color = this._color;
                return result;
            }
            equals(other) {
                return this === other || (other instanceof HeadFadeMaterialProperty && Cesium.Color.equals(this._color, other._color));
            }
        }

        this.headFadeMaterial = new HeadFadeMaterialProperty(Cesium.Color.WHITE.withAlpha(0.40));
        this.solidFutureMaterial = Cesium.Color.WHITE.withAlpha(0.40);

        this.engine._futureLine = this.engine.viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty(() => this._getFuturePositions(), false),
                width: 5.5,
                material: this.solidFutureMaterial,
                arcType: Cesium.ArcType.NONE,
                clampToGround: false,
            }
        });

        const FADE_MATERIAL_TYPE = 'PolylineTailFade';
        if (!Cesium.Material._materialCache.getMaterial(FADE_MATERIAL_TYPE)) {
            Cesium.Material._materialCache.addMaterial(FADE_MATERIAL_TYPE, {
                fabric: {
                    type: FADE_MATERIAL_TYPE,
                    uniforms: {
                        color: Cesium.Color.fromCssColorString('#FF8C42').withAlpha(0.90)
                    },
                    source: `
                        czm_material czm_getMaterial(czm_materialInput materialInput) {
                            czm_material material = czm_getDefaultMaterial(materialInput);
                            material.diffuse = color.rgb;
                            // materialInput.st.s goes 0.0 to 1.0 along the line.
                            // We square it for a smoother, softer alpha gradient at the tail end.
                            material.alpha = color.a * (materialInput.st.s * materialInput.st.s); 
                            return material;
                        }
                    `
                }
            });
        }

        class TailFadeMaterialProperty {
            constructor(color) {
                this._color = color;
                this._definitionChanged = new Cesium.Event();
            }
            get isConstant() { return true; }
            get definitionChanged() { return this._definitionChanged; }
            getType(time) { return FADE_MATERIAL_TYPE; }
            getValue(time, result) {
                if (!Cesium.defined(result)) result = {};
                result.color = this._color;
                return result;
            }
            equals(other) {
                return this === other || (other instanceof TailFadeMaterialProperty && Cesium.Color.equals(this._color, other._color));
            }
        }

        this.tailFadeMaterial = new TailFadeMaterialProperty(Cesium.Color.fromCssColorString('#FF8C42').withAlpha(0.90));
        this.solidPastMaterial = Cesium.Color.fromCssColorString('#FF8C42').withAlpha(0.90);

        this.engine._pastLine = this.engine.viewer.entities.add({
            polyline: {
                positions: new Cesium.CallbackProperty(() => this._getPastPositions(), false),
                width: 5.5,
                material: this.solidPastMaterial,
                arcType: Cesium.ArcType.NONE,
                clampToGround: false,
            }
        });

        const self = this.engine;
        this.engine.corridorEntity = {
            past: this.engine._pastLine,
            future: this.engine._futureLine,
            set show(val) {
                self._pathsVisible = val;
                if (self._pastLine)   self._pastLine.show   = val;
                if (self._futureLine) self._futureLine.show = val;
            },
            get show() {
                return self._pathsVisible;
            }
        };
    }

    _getCurrentSampleIndex() {
        const now = this.engine.viewer.clock.currentTime;
        for (let i = 0; i < this.engine._pathSamples.length - 1; i++) {
            if (Cesium.JulianDate.compare(this.engine._pathSamples[i + 1].julianDate, now) >= 0) {
                return i;
            }
        }
        return this.engine._pathSamples.length - 1;
    }

    _getPastPositions() {
        if (!this.engine._pathsVisible || this.engine._currentMode === 'COCKPIT') return [];
        if (!this.engine._pathSamples || this.engine._pathSamples.length < 2) return [];

        const now = this.engine.viewer.clock.currentTime;
        const idx = this._getCurrentSampleIndex();
        
        let positions = this.engine._pathSamples.slice(0, idx + 1).map(s => s.cartesian3);
        
        const aircraftCurrentPos = this.engine.positionProperty.getValue(now);
        if (aircraftCurrentPos) {
            positions.push(aircraftCurrentPos);
        }

        if (positions.length < 2) return [];

        positions.reverse();

        if (this.engine._currentMode !== 'TRACKING') {
            positions.reverse();
            return positions;
        }

        const MAX_DISTANCE_M = 100_000;
        const limited = [positions[0]];
        let accumulated = 0;
        
        for (let i = 1; i < positions.length; i++) {
            const dist = Cesium.Cartesian3.distance(
                positions[i - 1],
                positions[i]
            );
            accumulated += dist;
            limited.push(positions[i]);
            
            if (accumulated >= MAX_DISTANCE_M) {
                const overshoot = accumulated - MAX_DISTANCE_M;
                const segmentLength = dist;
                if (segmentLength > 0) {
                    const fraction = (segmentLength - overshoot) / segmentLength;
                    const finalPos = Cesium.Cartesian3.lerp(
                        positions[i - 1],
                        positions[i],
                        fraction,
                        new Cesium.Cartesian3()
                    );
                    limited[limited.length - 1] = finalPos;
                }
                break;
            }
        }
        
        limited.reverse();
        return limited;
    }

    _getFuturePositions() {
        if (!this.engine._pathsVisible || this.engine._currentMode === 'COCKPIT') return [];
        if (!this.engine._pathSamples || this.engine._pathSamples.length < 2) return [];

        const now = this.engine.viewer.clock.currentTime;
        const idx = this._getCurrentSampleIndex();
        
        const aircraftCurrentPos = this.engine.positionProperty.getValue(now);
        let positions;
        
        if (aircraftCurrentPos) {
            const remaining = this.engine._pathSamples.slice(idx + 1);
            positions = [aircraftCurrentPos, ...remaining.map(s => s.cartesian3)];
        } else {
            const remaining = this.engine._pathSamples.slice(idx);
            positions = remaining.map(s => s.cartesian3);
        }

        if (positions.length < 2) return [];

        if (this.engine._currentMode === 'TRACKING') {
            const MAX_DISTANCE_M = 20_000;
            const limited = [positions[0]];
            let accumulated = 0;
            for (let i = 1; i < positions.length; i++) {
                const dist = Cesium.Cartesian3.distance(
                    positions[i - 1],
                    positions[i]
                );
                accumulated += dist;
                limited.push(positions[i]);
                if (accumulated >= MAX_DISTANCE_M) {
                    const overshoot = accumulated - MAX_DISTANCE_M;
                    const segmentLength = dist;
                    if (segmentLength > 0) {
                        const fraction = (segmentLength - overshoot) / segmentLength;
                        const finalPos = Cesium.Cartesian3.lerp(
                            positions[i - 1],
                            positions[i],
                            fraction,
                            new Cesium.Cartesian3()
                        );
                        limited[limited.length - 1] = finalPos;
                    }
                    break;
                }
            }
            return limited;
        }

        return positions;
    }

    clearFlight() {
        if (this.engine._pastLine) { 
            this.engine.viewer.entities.remove(this.engine._pastLine); 
            this.engine._pastLine = null; 
        }
        if (this.engine._futureLine) { 
            this.engine.viewer.entities.remove(this.engine._futureLine); 
            this.engine._futureLine = null; 
        }
        this.engine._pathSamples = [];
        this.engine.corridorEntity = null;
    }
}
