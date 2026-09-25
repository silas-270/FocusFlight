# Dev features to revert before release

Temporary shortcuts added for testing that must be undone before shipping.
Checklist, not history — check items off as they're reverted.

- [x] **Return Home cooldown disabled** (real: 7 days) —
      `app/src/main/java/com/silas270/blocktime/data/model/HomeBaseCooldown.kt`:
      `isEligible()` has a dev-override `return true` at the top of the
      function, short-circuiting the real check below it (left in place,
      commented out). Delete the override line and uncomment the two
      lines under it.
- [x] **Change Home Base cooldown disabled** (real: 30 days) —
      same override as above, same function — `isEligible()` is shared
      by both cooldowns, so the one line disables both at once.
      While this override is in place, two assertions in
      `app/src/test/java/com/silas270/blocktime/data/model/HomeBaseCooldownTest.kt`
      (the `assertFalse` checks that a cooldown blocks a second action) fail, so
      `./gradlew test` is red. Reverting the override turns them green again —
      the tests are correct, the override is what's temporary.
- [x] **Skip-flight button in the in-flight UI** — moved into the in-flight debug
      menu (`app/src/debug/.../inflight/FlightDebugMenu.kt`), which release builds replace
      with an empty stand-in from `app/src/release/`. Nothing to revert; it never ships.
