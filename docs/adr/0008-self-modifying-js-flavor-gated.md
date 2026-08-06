# ADR 0008 — Self-Modifying JS Engine (Rhino) — Flavor-Gated

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS's "self-modifying engine" lets the AI write new skills for itself in JavaScript at runtime (using Mozilla Rhino JS engine). This is a flagship feature — it makes NOUS genuinely adaptable in a way no other assistant is.

However, **Google Play Policy** states:

> "Apps must not download executable code... This does not apply to code that runs in an interpreter that is part of an app distributed via Google Play (such as Lua, JavaScript, etc. interpreted by an embedded interpreter)... provided that the interpreter is not used to bypass Google Play policies."

The key phrase: "interpreted by an embedded interpreter". Rhino IS an embedded JS interpreter, so it should technically be allowed. **But** Play reviewers have historically rejected apps that ship JS engines for self-modifying behavior — they treat each case conservatively.

Risk assessment: shipping Rhino in `prod` flavor = high probability of Play rejection, weeks of appeal, possible account suspension.

## Decision

Adopt **flavor gating** for the self-modifying JS engine:

### Flavor Behavior
| Flavor | Self-Modify Available? | Why |
|---|---|---|
| `dev` | ✅ Yes | Developer testing |
| `internal` | ✅ Yes | Closed testing / sideload |
| `prod` (Play Store) | ❌ No | Play Policy compliance |

### Implementation
1. `:feature:selfmodify` module exists in all flavors (compile-time)
2. In `prod` flavor, `BuildConfig.ENABLE_SELF_MODIFY = false`
3. R8 removes all reachable Rhino code when flag is false
4. UI hides "Create custom skill" option when flag is false
5. Brain's Skill Router never invokes JS skills when flag is false
6. Existing JS skills (created in sideload, then user installs Play Store build) remain dormant — not deleted, just not executed

### User Migration Path
- User installs sideload build, creates JS skills, syncs to cloud (Zero-Knowledge Sync)
- User installs Play Store build — JS skills are visible but disabled
- User reinstalls sideload build — JS skills work again
- No data loss, no broken state

## Consequences

**Positive:**
- Play Store submission safe from policy rejection on this axis
- Power users still get full self-modify capability via sideload
- No data loss on flavor switch
- R8 strips dead code → smaller Play APK

**Negative:**
- Play Store users miss flagship feature
- Users confused why feature exists in sideload but not Play Store
- Two code paths to maintain

**Mitigation:**
- Play Store listing mentions "For full power, install from GitHub"
- In-app banner explains why feature is disabled in Play build
- README clearly documents the gating

## Alternatives Considered

- **Ship in all flavors, hope Play passes**: rejected — too risky, possible account ban
- **Remove entirely**: rejected — flagship feature, kills project identity
- **Replace with WebView-based JS execution**: rejected — WebView JS is sandboxed, can't access Android APIs that skills need
- **Server-side skill compilation**: rejected — defeats on-device privacy promise
