export class AtmosphereManager {
    constructor(engine) {
        this.engine = engine;
    }

    getFlightProgress() {
        if (!this.engine._startTime || !this.engine._stopTime) return 0.5;
        const total = Cesium.JulianDate.secondsDifference(this.engine._stopTime, this.engine._startTime);
        if (total <= 0) return 0.5;
        const elapsed = Cesium.JulianDate.secondsDifference(this.engine.viewer.clock.currentTime, this.engine._startTime);
        return Math.max(0, Math.min(1, elapsed / total));
    }

    applyAtmosphere() {
        const scene = this.engine.viewer.scene;

        if (scene.skyBox) scene.skyBox.show = true;
        if (scene.sun) scene.sun.show = false;
        if (scene.moon) scene.moon.show = false;
        
        if (scene.skyAtmosphere) {
            scene.skyAtmosphere.show = false;
        }
        scene.globe.showGroundAtmosphere = false;
        scene.fog.enabled = false;

        scene.light = new Cesium.SunLight();

        if (!this.engine._customAtmosphereStage) {
            const fs = `
                uniform sampler2D colorTexture;
                uniform sampler2D depthTexture;
                uniform float u_intensity;
                
                uniform vec4 u_sunsetRed;
                uniform vec4 u_sunsetOrange;
                
                in vec2 v_textureCoordinates;
                
                vec4 calculateSkyColor(vec3 dirWC, float h, float earthAngularRadius) {
                    // Quadratic scaling: stays normal on ground, shrinks aggressively in space to form a thin atmospheric rim
                    float sunsetHeight = min(0.15, earthAngularRadius * earthAngularRadius * 0.06); 
                    float dip = (1.0 - u_intensity) * sunsetHeight;
                    float effectiveH = h + dip;
                    
                    if (effectiveH > sunsetHeight) {
                        return vec4(0.0);
                    }
                    
                    float t = effectiveH / sunsetHeight;
                    
                    vec4 colorRed    = vec4(0.90, 0.35, 0.05, 1.0); 
                    vec4 colorOrange = vec4(0.95, 0.55, 0.10, 1.0); 
                    vec4 colorYellow = vec4(0.95, 0.75, 0.20, 1.0); 
                    
                    vec4 color;
                    
                    if (t < 0.45) {
                        float blend = smoothstep(0.0, 0.45, max(t, 0.0));
                        blend = pow(blend, 1.2); 
                        color = mix(colorRed, colorOrange, blend);
                    } else {
                        float blend = smoothstep(0.45, 1.0, t);
                        color = mix(colorOrange, colorYellow, blend);
                    }
                    
                    // Fade out alpha smoothly ABOVE the sunset only
                    // Below the horizon (t < 0), alpha remains 1.0 so the fog retains its opaque color!
                    // Scale alpha by u_intensity to completely turn off the shader when dark
                    color.a *= smoothstep(1.0, 0.0, t) * u_intensity;
                    
                    return color;
                }
                
                void main() {
                    float depth = czm_readDepth(depthTexture, v_textureCoordinates);
                    vec4 baseColor = texture(colorTexture, v_textureCoordinates);
                    
                    vec4 farPosEC = czm_windowToEyeCoordinates(gl_FragCoord.xy, 1.0);
                    vec3 dirWC = normalize(czm_inverseViewRotation * normalize(farPosEC.xyz));
                    
                    
                    // True angular math with exact WGS84 Ellipsoid radius
                    vec3 up = normalize(czm_viewerPositionWC);
                    float cosElevation = dot(dirWC, up);
                    float pixelAngle = acos(clamp(cosElevation, -1.0, 1.0)); 
                    
                    float camRadius = length(czm_viewerPositionWC);
                    
                    // Calculate exact radius of the earth directly below the camera
                    float a = 6378137.0;
                    float b = 6356752.314245;
                    float a2 = a * a;
                    float b2 = b * b;
                    float R = 1.0 / sqrt((up.x*up.x + up.y*up.y)/a2 + (up.z*up.z)/b2);
                    
                    float ratio = clamp(R / camRadius, 0.0, 1.0);
                    float earthAngularRadius = asin(ratio);
                    
                    float horizonTheta = 3.14159265 - earthAngularRadius;
                    float h = horizonTheta - pixelAngle;
                    
                    // Fix singularity: Calculate horizonDist using stable geometry to avoid precision collapse at low altitudes
                    float alt = max(0.0, camRadius - R);
                    float horizonDist = max(10.0, sqrt(alt * (camRadius + R)));
                    
                    vec4 artisticSky = calculateSkyColor(dirWC, h, earthAngularRadius);
                    
                    // NEW: Analytically compute distance to Earth Ellipsoid, immune to log depth scaling bugs
                    vec3 O_scaled = czm_viewerPositionWC * vec3(1.0/a, 1.0/a, 1.0/b);
                    vec3 D_scaled = dirWC * vec3(1.0/a, 1.0/a, 1.0/b);
                    float A_q = dot(D_scaled, D_scaled);
                    float B_q = 2.0 * dot(O_scaled, D_scaled);
                    float C_q = dot(O_scaled, O_scaled) - 1.0;
                    float delta_q = B_q*B_q - 4.0*A_q*C_q;
                    
                    float distEllipsoid = 10000000.0;
                    if (delta_q >= 0.0) {
                        distEllipsoid = (-B_q - sqrt(delta_q)) / (2.0 * A_q);
                    }
                    
                    // NEW: Bulletproof foreground detection using Globe depth
                    float globeDepth = czm_readDepth(czm_globeDepthTexture, v_textureCoordinates);
                    // Agnostic to Reverse Z vs Standard Z: if pixel depth differs from terrain depth, it is a 3D model.
                    float isForeground = (abs(depth - globeDepth) > 0.00005) ? 1.0 : 0.0;
                    
                    bool isSky = false;
                    // Check if it lies on the far plane (0.0 for Reverse Z, 1.0 for Standard)
                    if (depth > 0.99999 || depth < 0.00001) {
                        // Use exact WGS84 Ellipsoid intersection. This eliminates the fake floating horizon.
                        if (delta_q < 0.0 || B_q >= 0.0) {
                            isSky = true;
                        }
                    }
                    
                    vec3 finalColor;
                    
                    if (isSky) {
                        vec3 emptySky = mix(baseColor.rgb, vec3(0.02), u_intensity);
                        finalColor = mix(emptySky, artisticSky.rgb, artisticSky.a);
                    } else {
                        vec3 blurTerrain = baseColor.rgb;
                        
                        // Normalized distance from the closest point on Earth to the horizon
                        float tDist = clamp((distEllipsoid - alt) / (horizonDist - alt), 0.0, 1.0);
                        
                        // 3. Distance Fog merging perfectly into the sky
                        // Dynamically scale the thresholds based on camera altitude (ratio). 
                        // Use a steep power curve so it tightly hugs the Earth's limb for all space views, 
                        // and only sweeps across the landscape when entering the atmosphere!
                        float scaleWeight = pow(ratio, 10.0);
                        float distStart = mix(0.95, 0.1, scaleWeight);
                        float distEnd = mix(1.0, 0.8, scaleWeight);
                        float distFog = smoothstep(distStart, distEnd, tDist);
                        
                        // Quadratic scaling shrinks the haze band aggressively in deep space
                        float hazeBand = min(0.05, earthAngularRadius * earthAngularRadius * 0.02); 
                        
                        float angleStart = mix(0.95, 0.1, scaleWeight);
                        float angleEnd = mix(1.0, 0.7, scaleWeight);
                        float angleFogMask = smoothstep(angleStart, angleEnd, tDist);
                        float angleFog = smoothstep(-hazeBand, 0.0, h) * angleFogMask;
                        
                        float totalFog = clamp(max(distFog, angleFog), 0.0, 1.0);
                        totalFog *= (1.0 - isForeground);
                        
                        // TRUE BLUR: Cinematic 17-tap depth-of-field blur!
                        // Scale blur down to 0 when dark to completely disable the shader effects
                        float blurAmount = totalFog * 10.0 * u_intensity; 
                        if (blurAmount > 0.0) {
                            vec2 texel = 1.0 / czm_viewport.zw;
                            vec2 off1 = texel * blurAmount * 0.4;
                            vec2 off2 = texel * blurAmount * 0.8;
                            vec2 off3 = texel * blurAmount * 1.2;
                            
                            vec3 c = baseColor.rgb * 4.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off1.x, 0.0)).rgb * 2.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off1.x, 0.0)).rgb * 2.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(0.0, off1.y)).rgb * 2.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(0.0, -off1.y)).rgb * 2.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off2.x, 0.0)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off2.x, 0.0)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(0.0, off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(0.0, -off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off2.x, off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off2.x, -off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off2.x, -off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off2.x, off2.y)).rgb * 1.0;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off3.x, off3.y)).rgb * 0.5;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off3.x, -off3.y)).rgb * 0.5;
                            c += texture(colorTexture, v_textureCoordinates + vec2(off3.x, -off3.y)).rgb * 0.5;
                            c += texture(colorTexture, v_textureCoordinates + vec2(-off3.x, off3.y)).rgb * 0.5;
                            blurTerrain = c / 22.0;
                        }
                        
                        // To brighten the terrain without hue-shifting bright objects (like the orange polyline into yellow),
                        // we must preserve the exact ratios of the RGB channels when applying the multiplier.
                        float maxChannel = max(max(blurTerrain.r, blurTerrain.g), blurTerrain.b);
                        vec3 terrainColor = blurTerrain;
                        if (maxChannel > 0.0) {
                            float brightnessMultiplier = mix(2.0, 1.0, u_intensity);
                            float newMax = min(maxChannel * brightnessMultiplier, 1.0);
                            terrainColor = blurTerrain * (newMax / maxChannel);
                        }
                        
                        // 1. Reflection on the ground (Quadratic scaling shrinks reflection aggressively in space)
                        float reflectBand = min(0.15, earthAngularRadius * earthAngularRadius * 0.06);
                        float reflectionStrength = smoothstep(-reflectBand, 0.0, h) * smoothstep(0.05, 0.0, h); 
                        reflectionStrength *= smoothstep(0.0, 0.2, tDist); // Fade reflection in gradually
                        reflectionStrength *= (1.0 - isForeground); 
                        
                        vec3 reflectionColor = artisticSky.rgb * artisticSky.a * 0.5 * u_intensity; 
                        terrainColor += reflectionColor * reflectionStrength;
                        
                        // 2. Silhouette for foreground objects
                        float skyBacklight = smoothstep(-0.05, 0.05, h) * artisticSky.a;
                        float silhouette = 1.0 - (skyBacklight * isForeground * 0.9);
                        terrainColor *= silhouette;
                        
                        vec3 fogColor = mix(vec3(0.02), artisticSky.rgb, artisticSky.a);
                        // Make fog opacity match sky alpha to guarantee a seamless horizon
                        float fogOpacity = clamp(totalFog * max(u_intensity, artisticSky.a), 0.0, 1.0);
                        finalColor = mix(terrainColor, fogColor, fogOpacity);
                    }
                    
                    out_FragColor = vec4(finalColor, 1.0);
                }
            `;
            
            this.engine._customAtmosphereStage = new Cesium.PostProcessStage({
                fragmentShader: fs,
                uniforms: {
                    u_intensity: 1.0,
                    u_sunsetRed: () => Cesium.Color.fromCssColorString('#FF1100'),
                    u_sunsetOrange: () => Cesium.Color.fromCssColorString('#FF9900')
                }
            });
            scene.postProcessStages.add(this.engine._customAtmosphereStage);
        }
        
        this.engine._customAtmosphereStage.enabled = true;
        this.engine._sunsetMode = 'CUSTOM';

        if (this.engine._atmosphereTickListener) {
            this.engine.viewer.clock.onTick.removeEventListener(this.engine._atmosphereTickListener);
            this.engine._atmosphereTickListener = null;
        }

        this.engine._atmosphereTickListener = (clock) => {
            if (!this.engine._isDarkMode) return;

            const progress = this.getFlightProgress();
            
            const cycle = 0.5 + 0.5 * Math.cos(progress * Math.PI * 2.0);

            if (this.engine._customAtmosphereStage) {
                this.engine._customAtmosphereStage.uniforms.u_intensity = cycle;
            }
            
            scene.requestRender();
        };

        this.engine.viewer.clock.onTick.addEventListener(this.engine._atmosphereTickListener);
        scene.requestRender();
    }

    clearAtmosphere() {
        const scene = this.engine.viewer.scene;

        if (this.engine._atmosphereTickListener) {
            this.engine.viewer.clock.onTick.removeEventListener(this.engine._atmosphereTickListener);
            this.engine._atmosphereTickListener = null;
        }
        
        if (this.engine._customAtmosphereStage) {
            this.engine._customAtmosphereStage.enabled = false;
        }

        if (scene.skyBox) scene.skyBox.show = false;
        if (scene.sun) scene.sun.show = false;
        if (scene.moon) scene.moon.show = false;

        if (scene.skyAtmosphere) {
            scene.skyAtmosphere.show = false;
            scene.skyAtmosphere.brightnessShift = 0.0;
            scene.skyAtmosphere.hueShift = 0.0;
            scene.skyAtmosphere.saturationShift = 0.0;
            if ('atmosphereRayleighCoefficient' in scene.skyAtmosphere) {
                scene.skyAtmosphere.atmosphereRayleighCoefficient = new Cesium.Cartesian3(5.5e-6, 13.0e-6, 28.4e-6);
            }
            if ('atmosphereMieCoefficient' in scene.skyAtmosphere) {
                scene.skyAtmosphere.atmosphereMieCoefficient = new Cesium.Cartesian3(21.0e-6, 21.0e-6, 21.0e-6);
            }
        }
        scene.globe.showGroundAtmosphere = false;
        scene.globe.atmosphereHueShift = 0.0;
        scene.globe.atmosphereSaturationShift = 0.0;
        scene.globe.atmosphereBrightnessShift = 0.0;

        scene.fog.enabled = false;
        scene.light = new Cesium.SunLight();

        scene.requestRender();
    }

    setSunsetMode(mode) {
        this.engine._sunsetMode = mode;
        const scene = this.engine.viewer.scene;
        
        if (mode === 'DEFAULT') {
            if (this.engine._customAtmosphereStage) {
                this.engine._customAtmosphereStage.enabled = false;
            }
            if (scene.skyAtmosphere) {
                scene.skyAtmosphere.show = true;
            }
            if (scene.sun) scene.sun.show = true;
        } else {
            if (this.engine._customAtmosphereStage) {
                this.engine._customAtmosphereStage.enabled = true;
            }
            if (scene.skyAtmosphere) {
                scene.skyAtmosphere.show = false;
            }
            if (scene.sun) scene.sun.show = false;
        }
        scene.requestRender();
    }
}
