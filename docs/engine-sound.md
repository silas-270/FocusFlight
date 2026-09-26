# Engine sound

The in-flight engine noise is **synthesised in real time from the flight's own telemetry**. Nothing
is sampled or looped: there is no audio asset in the app and no recording of an aircraft went into
it. Every frequency is derived from the published geometry of a Rolls-Royce Trent XWB, the engine
an A350 flies behind, and the loudness follows what a flight crew would be commanding at that
point in the flight.

The sound is off by default, toggled from the in-flight settings panel, and remembered across
flights (`engine_sound_enabled`).

## Two halves and one number

| Half | File | Answers |
|---|---|---|
| Thrust model | `domain/EnginePowerModel.kt` | how hard are the engines working? |
| Synthesiser | `audio/EngineSoundSynth.kt` | what does an engine working that hard sound like? |
| Plumbing | `audio/EngineSoundEngine.kt` | `AudioTrack`, the audio thread, start and stop |

Neither half knows anything about the other beyond a single number: **N1**, the fan's speed as a
fraction of its redline. `InFlightViewModel` steps the model once per 33 ms timer tick and publishes
the result as `enginePower`; `InFlightScreen` collects that flow straight into the audio engine, so
a value that changes thirty times a second never passes through Compose state.

The number is N1 and not loudness on purpose. A turbofan idles at about a fifth of its redline fan
speed and never stops in flight, so the useful range is roughly 0.2 to 1.0, and the synthesiser
reads it as a physical speed from which it derives shaft rate, blade-pass frequency and blade-tip
Mach. Loudness is one of the things it computes from that, steeply
([below](#loudness)).

## The thrust model

### The schedule

The settings, in the order a flight goes, and the value the model settles at for a representative
telemetry reading at each point:

| Phase | N1 | Why |
|---|---|---|
| Parked, taxi | 0.20 | ground idle |
| **Takeoff roll** | **1.00** | the loudest the engines ever are; reached by about 25 m/s (≈ 50 kt) |
| Initial climb | 1.00 | held until the thrust reduction altitude |
| Climb | 0.88 | after thrust reduction, faded in between 300 and 1,200 m |
| Cruise | 0.82 | below the climb, above everything on the way down |
| Descent | 0.32 | flight idle: a jet descends with the thrust levers back |
| Approach | 0.58 | thrust comes back up to hold the glideslope with gear and flaps out |
| Flare, touchdown | 0.20 | retarded to idle |
| Reverse thrust | up to 0.78 | a window during the rollout |
| Taxi in | 0.20 | ground idle |

The shape matters more than the constants: **power is highest on the runway and steps down from
there**. The test suite asserts exactly that shape (the takeoff roll out-powers every other phase,
power steps down from takeoff through climb to cruise, the arrival goes approach → quiet flare →
reverse → idle) rather than the numbers, so retuning a constant does not mean rewriting the tests.

### Reading the telemetry

The model reads four raw values from `nativeGetTelemetry()`: progress (index 0), altitude in metres
(3), speed in m/s (4) and pitch in radians (6), plus the real time since the last tick. It reads the
raw doubles, not the rounded copies the HUD displays.

### Memoryless gates

Every decision in the schedule is a smooth function of the **current** sample only. There are no
rates and no differences between ticks, for three reasons:

- The engine reports pitch attitude directly, so climb and descent can be read off it. Differencing
  altitude to recover a climb rate would reconstruct a signal that is already given, and badly: at a
  33 ms tick the true change in altitude is a fraction of a metre, so a rate from rounded altitudes
  is mostly quantisation noise.
- The same holds for acceleration. A takeoff roll accelerates at little more than 1 m/s²;
  differencing a rounded speed thirty times a second cannot see it at all.
- A memoryless model makes the debug seek honest. Jump to any point of the flight and the power is
  what it would have been had the aircraft flown there, independent of how it arrived.

There is also no discrete flight-phase enum. Bucketing ticks into ground, climb and cruise produces
audible jump cuts whenever a reading crosses a threshold; every blend here is a `smoothstep` over a
continuous quantity instead, so there is no boundary to cross:

- **airborne**, from 25 m (the aircraft sits at about 5 m, since altitude is measured to the
  fuselage) to 130 m;
- **climbing** and **descending**, from pitch: a 1° dead band so level flight is unambiguous, full
  weight by 12° nose up or 8° nose down, well inside the profile's actual climb and descent attitudes;
- **thrust reduction** from 300 to 1,200 m, so the step down after takeoff is a fade;
- **approach** below 2,500 m, fully approach thrust below 400 m;
- a **level-flight baseline** that itself depends on altitude, falling to idle below 150 m.

### Two traps on the way down

Two different mechanisms can produce the same wrong-way-round surge of power on short final, and the
model guards against both.

**Reverse thrust is a window, not a floor.** It is gated on speed with an upper edge as well as a
lower one: the buckets deploy between 70 and 62 m/s (the aircraft touches down at about 72 m/s, so
the flare stays quiet), stay fully deployed down to 45 m/s, and stow by 30 m/s. Sampled, the rollout
reads 0.49 at 66 m/s, 0.78 at 55, 0.63 at 40 and idle at 10: a few seconds of roar after touchdown,
not the whole arrival. Gating it on deceleration instead would fire it down the entire approach,
because the profile bleeds speed the whole way.

**The level-flight baseline is altitude-dependent.** The pitch gates necessarily relax as the
attitude flattens, and the one place that happens near the ground is the flare. With a flat
cruise-thrust baseline, the flare would spool the engines back up seconds before touchdown.

### Takeoff roll versus landing rollout

Both are on the runway at overlapping speeds, so nothing local tells them apart. The model uses
progress for this one decision: ground readings before the middle of the flight are a takeoff roll,
after it a rollout. The switch happens between 40% and 60% progress, when the aircraft is at cruise
and the airborne gate gives the ground terms no weight at all, so the crossover is inaudible by
construction. It is the only use of progress in the model.

### The spool

The model's one piece of state is the spool: an asymmetric first-order lag toward the demanded N1,
with a time constant of 3.0 s winding up and 1.8 s winding down. A large turbofan takes seconds to
wind up and noticeably less to wind down, and that asymmetry is audible on every thrust change. From
idle at the start of the takeoff roll, the engines reach 90% of takeoff power after about 6.3 s. The
lag is computed in real time (`1 − exp(−dt/τ)`), so it does not depend on the tick rate, and a single
step is capped at 0.5 s so a tick that spans a backgrounded app cannot spool the engine instantly.

`settleAt(...)` skips the lag and returns the demanded value directly. The debug seek uses it:
spooling from wherever the previous seek left the engine would make the sound describe the drag
rather than the point being dragged to.

## How the sound is made

A real engine's spectrum has distinct parts that move independently, and reproducing those
mechanisms is what makes the result read as an aircraft rather than an appliance. A single filter
slope over noise has no spectral shape, no tonal structure and, being stationary, no movement.

Below, *drive* is N1 rescaled so that idle is 0 and takeoff is 1. N1 only spans about four fifths of
its range between the two, so almost every audible change is driven off drive rather than N1.

| Layer | Stands for | How |
|---|---|---|
| Rumble | low-frequency jet mixing noise | pink noise through a resonant band-pass (Q ≈ 1.1) swept 48 → 96 Hz with drive; the weight of the sound |
| Roar | bypass and exhaust shear | separately seeded pink noise per channel, high-passed at 140 Hz, through a gentle low-pass (Q ≈ 0.7) swept 330 → 1,850 Hz |
| Hiss | the sharp edge of the jet | the roar source high-passed at 2.2 kHz and kept quiet |
| Buzz-saw | supersonic fan-tip shocks | see below; only near full power |
| Fan tone | blade-pass frequency | N1 × 22 blades: 198 Hz at idle, 990 Hz at takeoff, with a second harmonic at a third of the level; ducked as the buzz-saw arrives |
| Core whine | the high-pressure spool | a sine from 2.4 to 4.8 kHz, loudest at idle and buried at power |

The broadband layers are amplitude-modulated by slow filtered noise (0.35 Hz for the rumble, 1.6 Hz
for the roar). A jet breathes; a perfectly steady noise bed is most of what makes synthesised engine
noise sound like a vacuum cleaner. The hiss is kept low for the same reason: the band above 2 kHz is
what tips a jet into sounding like a hairdryer when it leads.

### The buzz-saw

The part that makes the sound change character, not just volume, as power comes up.

The blade-tip Mach number follows from the fan's diameter and shaft speed:

```
tip Mach = π · 3.0 m · (45 Hz · N1) / 340 m/s ≈ 1.25 · N1
```

It passes 1 at about N1 0.80. Past that the fan blade tips are supersonic and each drags a weak
shock spiralling forward up the intake. Because no two blades are exactly alike, the pressure pattern
repeats once per **shaft revolution** rather than once per blade passage, so its harmonics fall on
every *engine order*, multiples of the shaft rate (45 Hz at redline), mostly below the blade-pass
frequency. That is why it is heard as a deep, ragged drone rather than a whistle, and why it only
exists near full power.

`BuzzSawGenerator` reproduces the mechanism rather than the result: an irregular train of 22 shocks
around one revolution, where each blade has its own shock strength and leaves its own pressure step
behind it. The irregularity is drawn **once** per instance and never changes, because it stands for
how this particular fan was built; blade-to-blade differences do not change from turn to turn. A
perfectly regular fan would put all its energy at the blade-pass frequency, a whistle near 1 kHz.
The pressure steps are band-limited as they are generated, not filtered afterwards, so they cannot
alias.

The onset is not a tuned threshold on N1. The buzz-saw fades in between tip Mach 1.02 and 1.22, which
is N1 ≈ 0.82 to 0.98. Cruise at 0.82 sits exactly at the onset, so it has none; climb at 0.88 has about
a third of it; takeoff has all of it. It lands where the physics puts it.

### Two engines

An airliner has two engines and they never run at exactly the same N1. The synthesiser runs them
0.3% either side of the commanded speed, so at takeoff their blade-pass tones are about 6 Hz apart,
and pans them apart (0.72 near, 0.42 far) with cross-bleed, because from inside the cabin both are
audible. The slow beat between the two tones is a large part of why a recording sounds like an
aircraft and a single synthesised engine does not. The broadband layers are shared between the two,
since at any listening position they have merged into one wash.

### Loudness

The master level is `0.16 + 0.84 · drive³`: steeply super-linear, because jet noise power grows with
a high power of jet velocity, so the difference between idle and takeoff is not a fader move. Idle is
quiet but never silent. Measured on the synthesiser's own output:

| N1 | RMS |
|---|---|
| 0.20, idle | 0.028 |
| 0.82, cruise | 0.123 |
| 0.88, climb | 0.158 |
| 1.00, takeoff | 0.274 |

about a tenfold change from idle to takeoff. The output passes through a DC blocker and a `tanh` soft
clipper with 12% headroom, so it can never hard-clip.

## Where the numbers come from

| Quantity | Value | Consequence |
|---|---|---|
| Fan diameter | 3.0 m (118 in) | with shaft speed, the tip Mach, and so the buzz-saw onset |
| Fan blades | 22 | blade-pass frequency is the shaft rate × 22 |
| LP shaft speed at redline | about 2,700 rpm, 45 Hz | the shaft rate, the buzz-saw's fundamental |
| Speed of sound | 340 m/s | the tip Mach |

Changing one of these moves the whole tonal structure coherently, which is the point of deriving
frequencies rather than choosing them by ear.

Further reading:

- [Buzz-saw noise, overview](https://www.sciencedirect.com/topics/engineering/buzz-saw-noise) and
  [Buzzsaw noise predictions for modern turbofans](https://www.researchgate.net/publication/268582453_Buzzsaw_Noise_Predictions_for_Modern_Turbofans):
  the supersonic-tip mechanism, why the tones fall on engine orders below blade-pass, and the
  sawtooth pressure signature.
- [Rolls-Royce Trent XWB](https://en.wikipedia.org/wiki/Rolls-Royce_Trent_XWB): fan diameter, blade
  count, three-shaft architecture.
- [Takeoff, climb and cruise](https://docs.flybywiresim.com/pilots-corner/a32nx/a32nx-beginner-guide/takeoff-climb-cruise/):
  thrust reduction after takeoff, and the direction of the schedule.

## Real-time plumbing

`EngineSoundEngine` streams 44.1 kHz, 16-bit interleaved stereo to an `AudioTrack` in `MODE_STREAM`,
from a dedicated thread at audio priority, in blocks of 512 frames (about 11.6 ms). The synthesiser
uses the standard control-rate / audio-rate split: gains, filter coefficients and frequencies are
recomputed once per block and held across it, which at that block size is far faster than any engine
spools. On top of the model's spool, the synthesiser glides toward each new N1 with a 30 ms time
constant purely as an anti-click measure, so even a seek across the whole flight cannot produce a
discontinuity.

`setEnginePower` stores the latest value in a volatile field and may be called from any thread at any
rate. Starting snaps the glide straight to the commanded speed, so a flight resumed at cruise comes
back at cruise rather than spooling up from idle. The engine stops on `ON_STOP` and restarts on
`ON_START`; there is no background audio.

Noise comes from a xorshift64\* generator rather than `java.util.Random`, which is synchronised, and
the synthesiser draws several random numbers per sample per noise source, several hundred thousand a
second.

## Testing

`EngineSoundSynth` has **no Android dependency**, which is the reason it is separate from
`EngineSoundEngine`. It renders on the JVM, so `EngineSoundSynthTest` can render it and measure the
result instead of anyone having to listen: peak and RMS at every power setting, band balance,
brightness, blade-pass tracking, engine-order prominence, stereo correlation, and the largest
sample-to-sample step after a sudden power change.

Two of those assertions carry the design:

- **The spectrum is low-heavy at every power setting**: average energy between 40 and 300 Hz exceeds energy
  from 3 to 9 kHz by at least four to one. A hiss-forward balance is the vacuum-cleaner signature.
- **Engine orders stand out at takeoff and not at cruise.** The test measures the energy on shaft
  orders 8 to 16 against the energy halfway between them. Measured, that ratio is about 8.7 at
  takeoff, 1.8 at climb, 1.05 at cruise and 1.15 at idle; the test requires more than 3 at takeoff
  and less than 2 at cruise and idle. This is the buzz-saw existing only where the tips are actually
  supersonic.

Because the two engines sit 0.3% either side of the nominal speed, a spectral probe placed exactly on
a nominal frequency falls between the two tones and reads the gap. Every tonal measurement probes
both.

`EnginePowerModelTest` checks the schedule's shape as described above, the spool's asymmetry, the
cap on a long tick, and that output stays in range for readings the engine could never produce.
