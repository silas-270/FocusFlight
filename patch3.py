import re

with open("/home/silas270/CesiumRS/crates/cesium-engine/src/render/wgpu_state.rs", "r") as f:
    code = f.read()

# Make window and surface optional
code = code.replace("pub surface: wgpu::Surface<'a>,", "pub surface: Option<wgpu::Surface<'a>>,")
code = code.replace("pub window: Arc<Window>,", "pub window: Option<Arc<Window>>,")
code = code.replace("pub egui_state: EguiState,", "pub egui_state: Option<EguiState>,")

# Update new function signature
code = code.replace(
    "pub async fn new(\n        window: Arc<Window>,\n        engine_config: crate::globe::tiles::config::TileEngineConfig,\n        mut extension: Option<Box<dyn crate::core::extension::GlobeExtension>>,\n    ) -> Self {",
    "pub async fn new(\n        window: Option<Arc<Window>>,\n        headless_size: Option<winit::dpi::PhysicalSize<u32>>,\n        engine_config: crate::globe::tiles::config::TileEngineConfig,\n        mut extension: Option<Box<dyn crate::core::extension::GlobeExtension>>,\n    ) -> Self {"
)

# Replace new function top body
old_new_body = """        let size = window.inner_size();
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::all(),
            ..Default::default()
        });

        let surface = instance.create_surface(window.clone()).unwrap();

        let adapter = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::default(),
                compatible_surface: Some(&surface),
                force_fallback_adapter: false,
            })
            .await
            .unwrap();"""

new_new_body = """        let size = window.as_ref().map(|w| w.inner_size()).unwrap_or(headless_size.unwrap_or(winit::dpi::PhysicalSize::new(800, 600)));
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::all(),
            ..Default::default()
        });

        let surface = window.as_ref().map(|w| instance.create_surface(w.clone()).unwrap());

        let adapter = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::default(),
                compatible_surface: surface.as_ref(),
                force_fallback_adapter: false,
            })
            .await
            .unwrap();"""
code = code.replace(old_new_body, new_new_body)

# Replace surface capabilities
old_caps = """        let surface_caps = surface.get_capabilities(&adapter);
        let surface_format = surface_caps
            .formats
            .iter()
            .copied()
            .find(|f| f.is_srgb())
            .unwrap_or(surface_caps.formats[0]);
        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
            format: surface_format,
            width: size.width,
            height: size.height,
            present_mode: surface_caps.present_modes[0],
            alpha_mode: surface_caps.alpha_modes[0],
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };"""

new_caps = """        let surface_format = surface.as_ref().map(|s| {
            let caps = s.get_capabilities(&adapter);
            caps.formats.iter().copied().find(|f| f.is_srgb()).unwrap_or(caps.formats[0])
        }).unwrap_or(wgpu::TextureFormat::Rgba8UnormSrgb);
        let present_mode = surface.as_ref().map(|s| s.get_capabilities(&adapter).present_modes[0]).unwrap_or(wgpu::PresentMode::Fifo);
        let alpha_mode = surface.as_ref().map(|s| s.get_capabilities(&adapter).alpha_modes[0]).unwrap_or(wgpu::CompositeAlphaMode::Auto);

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
            format: surface_format,
            width: size.width,
            height: size.height,
            present_mode,
            alpha_mode,
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };"""
code = code.replace(old_caps, new_caps)

# Replace EguiState initialization
old_egui = """        let egui_state = EguiState::new(
            egui_ctx.clone(),
            egui::ViewportId::ROOT,
            &window,
            Some(window.scale_factor() as f32),
            None,
            Some(2048),
        );"""

new_egui = """        let egui_state = window.as_ref().map(|w| EguiState::new(
            egui_ctx.clone(),
            egui::ViewportId::ROOT,
            w.as_ref(),
            Some(w.scale_factor() as f32),
            None,
            Some(2048),
        ));"""
code = code.replace(old_egui, new_egui)

# Fix resize
code = code.replace("self.surface.configure(&self.device, &self.config);", "if let Some(s) = &self.surface { s.configure(&self.device, &self.config); }")

# Fix take_egui_input
code = code.replace("let raw_input = self.egui_state.take_egui_input(&self.window);", "if self.egui_state.is_none() { return; }\n        let e = self.egui_state.as_mut().unwrap();\n        let raw_input = e.take_egui_input(self.window.as_ref().unwrap());")

# Fix scale factor
code = code.replace("pixels_per_point: self.window.scale_factor() as f32,", "pixels_per_point: self.window.as_ref().unwrap().scale_factor() as f32,")

# Fix handle_platform_output
code = code.replace("""self.egui_state.handle_platform_output(
            &self.window,
            full_output.platform_output,
        );""", """e.handle_platform_output(
            self.window.as_ref().unwrap(),
            full_output.platform_output,
        );""")

# Fix render method outputs
old_render_out = """        let output = self.surface.get_current_texture()?;
        let view = output
            .texture
            .create_view(&wgpu::TextureViewDescriptor::default());"""
            
new_render_out = """        let mut output = None;
        let mut headless_texture = None;
        let mut view_opt = None;
        if let Some(s) = &self.surface {
            let out = s.get_current_texture()?;
            view_opt = Some(out.texture.create_view(&wgpu::TextureViewDescriptor::default()));
            output = Some(out);
        } else {
            let tex = self.device.create_texture(&wgpu::TextureDescriptor {
                label: Some("Headless Output"),
                size: wgpu::Extent3d { width: self.config.width, height: self.config.height, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: self.config.format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
                view_formats: &[],
            });
            view_opt = Some(tex.create_view(&wgpu::TextureViewDescriptor::default()));
            headless_texture = Some(tex);
        }
        let view = view_opt.unwrap();"""
code = code.replace(old_render_out, new_render_out)

# Fix capture
old_cap = """        let mut captured_pixels = None;
        if capture_memory {
            captured_pixels = Some(self.capture_pixels(&output.texture));
        } else if let Some(out_path) = screenshot_out {
            self.capture_screenshot(&output.texture, out_path);
        }

        output.present();"""

new_cap = """        let mut captured_pixels = None;
        let tex = output.as_ref().map(|o| &o.texture).unwrap_or_else(|| headless_texture.as_ref().unwrap());
        if capture_memory {
            captured_pixels = Some(self.capture_pixels(tex));
        } else if let Some(out_path) = screenshot_out {
            self.capture_screenshot(tex, out_path);
        }

        if let Some(out) = output {
            out.present();
        }"""
code = code.replace(old_cap, new_cap)

with open("/tmp/patched3.rs", "w") as f:
    f.write(code)

