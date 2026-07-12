import re

with open("/home/silas270/CesiumRS/crates/cesium-engine/src/render/wgpu_state.rs", "r") as f:
    code = f.read()

code = code.replace("pub surface: wgpu::Surface<'a>,", "pub surface: Option<wgpu::Surface<'a>>,")
code = code.replace("pub window: Arc<Window>,", "pub window: Option<Arc<Window>>,")
code = code.replace("pub egui_state: EguiState,", "pub egui_state: Option<EguiState>,")

new_fn = """pub async fn new(
        window: Option<Arc<Window>>,
        headless_size: Option<winit::dpi::PhysicalSize<u32>>,
        engine_config: crate::globe::tiles::config::TileEngineConfig,
        mut extension: Option<Box<dyn crate::core::extension::GlobeExtension>>,
    ) -> Self {
        let size = window.as_ref().map(|w| w.inner_size()).unwrap_or(headless_size.unwrap_or(winit::dpi::PhysicalSize::new(800, 600)));
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

code = re.sub(r'pub async fn new\([\s\S]*?\.unwrap\(\);', new_fn, code)

# replace surface_caps / format logic
caps_old = """let surface_caps = surface.get_capabilities(&adapter);
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

caps_new = """let surface_format = surface.as_ref().map(|s| {
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

code = code.replace(caps_old, caps_new)

# replace EguiState
egui_old = """let egui_state = EguiState::new(
            egui_ctx.clone(),
            egui::ViewportId::ROOT,
            &window,
            Some(window.scale_factor() as f32),
            None,
            Some(2048),
        );"""
egui_new = """let egui_state = window.as_ref().map(|w| EguiState::new(
            egui_ctx.clone(),
            egui::ViewportId::ROOT,
            w.as_ref(),
            Some(w.scale_factor() as f32),
            None,
            Some(2048),
        ));"""
code = code.replace(egui_old, egui_new)

# resize
code = code.replace("self.surface.configure(&self.device, &self.config);", "if let Some(s) = &self.surface { s.configure(&self.device, &self.config); }")

# update_logic
code = code.replace("let raw_input = self.egui_state.take_egui_input(&self.window);", "if let Some(e) = &mut self.egui_state { let raw_input = e.take_egui_input(self.window.as_ref().unwrap());")
code = code.replace("pixels_per_point: self.window.scale_factor() as f32,", "pixels_per_point: self.window.as_ref().unwrap().scale_factor() as f32,")
code = code.replace("self.egui_state.handle_platform_output(\n            &self.window,\n            full_output.platform_output,\n        );", "e.handle_platform_output(self.window.as_ref().unwrap(), full_output.platform_output); }")

# render pass
output_old = """let output = self.surface.get_current_texture()?;
        let view = output
            .texture
            .create_view(&wgpu::TextureViewDescriptor::default());"""
output_new = """let mut output = None;
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
code = code.replace(output_old, output_new)

cap_old = """let mut captured_pixels = None;
        if capture_memory {
            captured_pixels = Some(self.capture_pixels(&output.texture));
        } else if let Some(out_path) = screenshot_out {
            self.capture_screenshot(&output.texture, out_path);
        }

        output.present();"""
cap_new = """let mut captured_pixels = None;
        let tex = output.as_ref().map(|o| &o.texture).unwrap_or_else(|| headless_texture.as_ref().unwrap());
        if capture_memory {
            captured_pixels = Some(self.capture_pixels(tex));
        } else if let Some(out_path) = screenshot_out {
            self.capture_screenshot(tex, out_path);
        }

        if let Some(out) = output {
            out.present();
        }"""
code = code.replace(cap_old, cap_new)

# render_egui
code = code.replace("pub fn render_egui(", "pub fn render_egui(\n        &mut self,\n        encoder: &mut wgpu::CommandEncoder,\n        view: &wgpu::TextureView,\n        mut ui_closure: impl FnMut(&egui::Context, &mut Self),\n    ) {\n        if self.egui_state.is_none() { return; }")
code = re.sub(r'pub fn render_egui\([\s\S]*?\{[\s\S]*?\}', cap_new, code) # Oops, avoid regex here
code = code.replace("if self.egui_state.is_none() { return; }\n        if self.egui_state.is_none() { return; }", "if self.egui_state.is_none() { return; }")

with open("/tmp/patched.rs", "w") as f:
    f.write(code)
