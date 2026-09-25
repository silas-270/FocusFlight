# Engine sound

## What it is

The in-flight engine noise is **synthesised in real time from the flight's own telemetry**.
Nothing is sampled or looped: there is no audio asset anywhere in the app, and no recording
of an aircraft was used to make it. Every frequency is derived from the published geometry
of a Rolls-Royce Trent XWB — the engine an A350 actually flies behind — and the loudness is
derived from what a flight crew would be commanding at that point in the flight.

It is off by default and toggled per-session from the in-flight settings overlay.

## The two halves

They are deliberately separate, and they are separate for a reason worth keeping: one asks
*how hard are the engines working*, the other asks *what does an engine working that hard
sound like*. Neither needs to know anything about the other beyond a single number.

| Half | File | Owns |
|---|---|---|
| Thrust schedule | `domain/EnginePowerModel.kt` | Telemetry → N1 (fan speed as a fraction of redline) |
| Synthesis | `audio/EngineSoundSynth.kt` | N1 → interleaved stereo PCM |
| Plumbing | `audio/EngineSoundEngine.kt` | `AudioTrack`, the audio thread, start/stop |

`InFlightViewModel` steps the model once per timer tick and publishes the result as
`enginePower`; `InFlightScreen` collects that straight to the audio thread.

The number passed between them is **N1, not loudness**. A turbofan idles at roughly a fifth
of its redline fan speed and never reaches zero in flight, so the useful range is about
0.20–1.0. The synth is what turns that into a level, and it does so steeply — see *Loudness*
below.

## The thrust schedule

What the model reproduces, in the order a flight goes:

| Phase | N1 | Why that value |
|---|---|---|
| Taxi / parked | 0.20 | Ground idle |
| **Takeoff roll** | **1.00** | The loudest the engines ever are |
| Initial climb | 1.00 | Held until the thrust reduction altitude (~1000 ft) |
| Climb | 0.88 | After thrust reduction |
| Cruise | 0.82 | Below the climb, above everything on the way down |
| Descent | 0.32 | A jet descends at flight idle |
| Approach | 0.58 | Thrust comes back up to hold the glideslope with gear and flaps out |
| Flare / touchdown | 0.20 | Retarded to idle |
| Reverse thrust | 0.78 | A few seconds of the rollout, stowed by 30 m/s |

The shape matters more than the numbers: **power is highest on the runway and steps down
from there.** Anything that makes the ground quieter than cruise, or makes short final
louder than the climb, is wrong.

Between those points the model applies an asymmetric first-order lag — the spool. A large
turbofan takes about three seconds to wind up and rather less to wind down, and that
asymmetry is audible on every thrust change. It is expressed in real time, so it does not
depend on the tick rate.

### The schedule is memoryless

Every gate is a smooth function of the **current telemetry sample only**. No rates, no
differences between ticks. This is a rule, not an implementation detail — reintroducing a
derivative reintroduces the whole class of bug:

- The engine reports pitch attitude directly (`nativeGetTelemetry` index 6). Differencing
  altitude to recover a climb rate reconstructs a signal that is already given, and does it
  badly: altitude reaches the HUD as a truncated `Int`, and at a 33 ms tick the real change
  is a fraction of a metre, so the "rate" is mostly quantisation noise.
- The same goes for acceleration. A takeoff roll accelerates at barely over 1 m/s²;
  differencing an `Int` km/h reading at 30 Hz cannot see it at all.
- Memoryless makes the debug scrubber honest. Drag to any point in the flight and the power
  is what it would have been had you flown there, with no dependence on how you arrived.

So the model reads raw telemetry doubles, **not** the rounded copies the HUD displays.

There is no discrete flight-phase enum either. Bucketing ticks into GROUND/CLIMB/CRUISE
produces audible jump-cuts as a reading ticks across a threshold; every blend is a
`smoothstep` over a continuous quantity instead, so there is no boundary to cross.

### Separating the takeoff roll from the landing rollout

They are the same altitude at overlapping speeds, so nothing local distinguishes them. The
model uses flight progress (`nativeGetTelemetry` index 0) — and that term crosses at
mid-cruise, where the ground gate holds its weight at zero, so the crossover is inaudible by
construction. This is the one place progress is used, and it is why it can be used safely.

### Two traps in the arrival

Both produce the same wrong-way-round surge on short final, by different routes, and both
are guarded:

1. **Reverse thrust is a window, not a floor.** Gated on speed with an upper edge as well as
   a lower one, so it is silent through the flare, roars through the fast part of the
   rollout, and is stowed well before taxi speed. Gating it on deceleration instead would
   fire it down the entire approach, since the profile bleeds speed the whole way.
2. **The level-flight baseline is altitude-dependent.** The pitch gates necessarily relax as
   an attitude flattens out, and the one place that happens near the ground is the flare. A
   flat cruise-thrust baseline would spool the engines back up seconds before touchdown.

## How the sound is made

A real engine's spectrum has distinct parts that move independently. Reproducing the
mechanisms rather than the result is what makes it read as an aircraft rather than as an
appliance — a single filter slope over noise has no spectral shape, no tone structure, and,
being stationary, no movement.

| Layer | Stands for | Behaviour |
|---|---|---|
| Rumble | Low-frequency jet mixing noise | Resonant band-pass, 48→96 Hz with power. The weight of the sound |
| Roar | Bypass and exhaust shear | Gentle low-pass, 330→1850 Hz. Separately seeded per channel for width |
| Hiss | The sharp edge of the jet | High-passed at 2.2 kHz, kept quiet — this band is what tips a jet into sounding like a hairdryer if it leads |
| Buzz-saw | Supersonic fan-tip shocks | See below. Only exists near full power |
| Fan tone | Blade-pass frequency | N1 × 22 blades: 198 Hz at idle, 990 Hz at takeoff. Ducked as the buzz-saw arrives |
| Core whine | HP spool | 2.4→4.8 kHz, loudest at idle and buried at power |

The broadband layers are modulated by slow filtered noise. A jet breathes; a static filtered
noise bed does not, and that stillness is most of what makes synthesised engine noise sound
like a vacuum cleaner.

### The buzz-saw

The distinctive part, and the reason the sound changes character rather than just volume as
power comes up.

Past roughly N1 0.85 the fan blade **tips go supersonic** — at redline the tip Mach is about
1.25 — and each blade drags a weak shock spiralling forward up the intake. Because no two
blades are identical, the pressure pattern repeats once per **shaft revolution** rather than
once per blade passage. Its harmonics therefore fall on every *engine order* — multiples of
the ~45 Hz shaft rate — and mostly **below** blade-pass frequency. That is why it is heard as
a deep, ragged drone rather than a whistle, and why it appears only near full power.

`BuzzSawGenerator` reproduces that mechanism directly: an irregular shock train around one
revolution, where each blade has its own shock strength and leaves its own pressure step
behind it. The irregularity is drawn **once** per instance, not per revolution — it stands
for how that particular fan was built, and blade-to-blade differences do not change from turn
to turn. A perfectly regular fan would put all its energy at blade-pass frequency: a whistle
around 1 kHz, not a drone.

The onset is not a tuned threshold. It is computed from tip Mach, which comes from fan
diameter and shaft speed, so it lands where the physics puts it.

### Two engines

An airliner has two, and they are never at exactly the same N1. The synth runs them 0.3%
either side of the commanded speed and pans them apart, with cross-bleed because from inside
the cabin you hear both. The few-Hz beat between their blade-pass tones is a large part of
why a recording sounds like an aircraft and a single synthesised engine does not.

### Loudness

Master level is steeply super-linear in the normalised drive, because jet noise power grows
with a high power of jet velocity. The difference between idle and takeoff is not a fader
move — measured, it is about a ninefold change in RMS.

## Where the numbers come from

Every frequency is derived from Trent XWB geometry rather than dialled in by ear. Changing
one of these changes the whole tonal structure coherently, which is the point.

| Quantity | Value | Consequence |
|---|---|---|
| Fan diameter | 3.0 m (118 in) | With shaft speed, gives tip Mach — so the buzz-saw onset |
| Fan blades | 22 | Blade-pass frequency = shaft rate × 22 |
| LP shaft at redline | ~2700 rpm → 45 Hz | Shaft rate; the buzz-saw's fundamental |

Background, if you want to read further:

- [Buzz-saw noise — overview](https://www.sciencedirect.com/topics/engineering/buzz-saw-noise)
  and [Buzzsaw noise predictions for modern turbofans](https://www.researchgate.net/publication/268582453_Buzzsaw_Noise_Predictions_for_Modern_Turbofans)
  — the supersonic-tip mechanism, why the tones land on engine orders below blade-pass, and
  the sawtooth pressure signature.
- [Rolls-Royce Trent XWB](https://en.wikipedia.org/wiki/Rolls-Royce_Trent_XWB) — fan
  diameter, blade count, three-shaft architecture.
- [Takeoff, climb and cruise](https://docs.flybywiresim.com/pilots-corner/a32nx/a32nx-beginner-guide/takeoff-climb-cruise/)
  — the thrust reduction after takeoff, and the direction of the schedule.

## Testing and retuning

`EngineSoundSynth` has **no Android dependency**, which is the whole reason it is a separate
class from `EngineSoundEngine`. It renders on the JVM, so "does this still sound like an
engine" is a check rather than a listening session: `EngineSoundSynthTest` renders the output
and measures it — peak and RMS at every power setting, band balance, blade-pass prominence,
engine-order prominence, and stereo correlation.

Those tests pin the **shape**, not the constants, so retuning does not mean rewriting them.
Two assertions are load-bearing and should not be weakened:

- **The spectrum is low-heavy at every power setting.** A hiss-forward balance is the
  vacuum-cleaner signature.
- **Engine orders stand above the broadband at takeoff and not at cruise.** This is the
  buzz-saw existing only where the tips are actually supersonic. Measured prominence is about
  9× at N1 1.0 and about 1× at N1 0.82.

> [!NOTE]
> Both engines sit a few tenths of a percent either side of the commanded speed, so a
> spectral probe placed exactly on a nominal frequency falls *between* the two tones and
> reads the gap rather than the peak. Every tonal measurement must probe both.

`EnginePowerModelTest` covers the schedule the same way — that the takeoff roll out-powers
every other phase, that power steps down through the climb to cruise rather than up, and that
the arrival goes approach → quiet flare → reverse → taxi idle in that order.

For auditioning by ear, the in-flight screen has a `BuildConfig.DEBUG`-gated scrubber that
drags through a whole flight instantly, showing progress, altitude, speed and N1. It sits
collapsed behind a "DBG" chip in the top-left corner. Scrubbing pauses the timer, and the
panel then offers RESUME; closing the panel resumes the timer too. It is
temporary and is meant to be deleted along with its call site once tuning is finished.
