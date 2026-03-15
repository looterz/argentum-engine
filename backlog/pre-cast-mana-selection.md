# Pre-Cast Mana Source Selection

## Problem

When casting a spell, the engine auto-taps lands via ManaSolver. The opponent immediately sees which lands were tapped,
leaking strategic information (e.g., leaving blue mana open hints at a counterspell). The "retap" feature lets you
change tapping after casting, but the opponent already saw the initial taps.

**Goal:** Let the player optionally choose which mana sources to tap *before* the action goes on the stack.

## UX Design

- **Drag to cast** — always auto-tap (no change).
- **Click to cast** — action menu shows each mana-costing action (Cast, Cycle, Activate, Turn Face-Up) with a small
  land icon (`ms ms-dfc-land` from mana font) next to the button. Clicking the icon enters mana selection mode for
  that action; clicking the button itself does normal auto-tap.
- **Mana selection mode** — same UI as retap: valid sources get blue highlight, selected sources get green highlight,
  mana progress indicator in bottom-right, Confirm/Cancel buttons. Sources from `autoTapPreview` are pre-selected as
  defaults.
- **On confirm** — the action is sent with `PaymentStrategy.Explicit` (or equivalent), constraining the engine to only
  use the chosen sources. The normal post-selection flow continues (targeting, X selection, etc.).

## Implementation Plan

### Phase 1: Engine — Add `paymentStrategy` to all mana-costing actions

Currently only `CastSpell` and `TurnFaceUp` have a `paymentStrategy` field. Add it to:

**File: `rules-engine/.../core/GameAction.kt`**
- `ActivateAbility`: add `val paymentStrategy: PaymentStrategy = PaymentStrategy.AutoPay`
- `CycleCard`: add `val paymentStrategy: PaymentStrategy = PaymentStrategy.AutoPay`
- `TypecycleCard`: add `val paymentStrategy: PaymentStrategy = PaymentStrategy.AutoPay`

### Phase 2: Engine — Support `Explicit` payment in handlers

**File: `rules-engine/.../handlers/actions/ability/ActivateAbilityHandler.kt`**

The existing `autoTapForManaCost()` method (line ~725) always calls `manaSolver.solve()` without an exclude set. When
`paymentStrategy` is `Explicit`:
1. Compute `excludeSources` = all available sources NOT in `manaAbilitiesToActivate`.
2. Pass `excludeSources` to `manaSolver.solve()`.
3. Validation: also pass `excludeSources` to `manaSolver.canPay()` during validation.

The method already handles floating mana pool → partial pay → tap remainder. The only change is constraining which
sources can be tapped.

**File: `rules-engine/.../handlers/actions/ability/CycleCardHandler.kt`**

Same pattern as ActivateAbilityHandler — find where `manaSolver.solve()` is called and add `excludeSources` when
`paymentStrategy` is `Explicit`.

**File: `rules-engine/.../handlers/actions/spell/CastSpellHandler.kt`**

Already done in the current branch. `explicitPay` mirrors `autoPay` but constrains sources via `excludeSources`.
`validatePayment` validates with `manaSolver.canPay(excludeSources)`.

### Phase 3: Server — Attach `availableManaSources` to all mana-costing actions

**File: `game-server/.../protocol/ServerMessage.kt`**

Already done: `LegalActionInfo` has `availableManaSources: List<RetapSourceInfo>? = null`.

**File: `game-server/.../legalactions/LegalActionsCalculator.kt`**

Already done for CastSpell. Extend to also attach to `ActivateAbility`, `CycleCard`, `TypecycleCard`, and
`TurnFaceUp` actions. The same `manaSourceInfos` list is shared by all actions (computed once at the top of
`calculate()`).

Only attach when the action has a mana cost (i.e., `manaCostString` is set or `isAffordable` implies mana was
checked).

### Phase 4: Client types

**File: `web-client/src/types/messages.ts`**

Already done: `LegalActionInfo` has `availableManaSources?: readonly RetapSourceInfo[]`.

**File: `web-client/src/types/actions.ts`**

Add `paymentStrategy` to the TypeScript action types for `ActivateAbility`, `CycleCard`, `TypecycleCard` (it already
exists on `CastSpell` and `TurnFaceUp`).

### Phase 5: Client — ActionMenu icon

**File: `web-client/src/components/ui/ActionMenu.tsx`**

Remove the separate "Choose Mana" `ActionOption`. Instead, in `ActionOptionButton`, render a small land icon next to
the button for any action that has `availableManaSources`:

```tsx
{option.action?.availableManaSources?.length && (
  <button
    className={styles.manaSelectIcon}
    onClick={(e) => {
      e.stopPropagation()  // Don't trigger the main button
      startManaSelection(option.action)
    }}
    title="Choose which lands to tap"
  >
    <i className="ms ms-dfc-land" />
  </button>
)}
```

Style the icon button as a small (24x24), subtle, semi-transparent circle that appears to the right of the action
button (or inside it, right-aligned). On hover, it should brighten.

### Phase 6: Client — Mana selection state

**File: `web-client/src/store/slices/types.ts`**

Already done: `ManaSelectionState` interface.

**File: `web-client/src/store/slices/uiSlice.ts`**

Already done: `startManaSelection`, `toggleManaSource`, `cancelManaSelection`, `confirmManaSelection`.

Update `confirmManaSelection` to handle all action types (not just CastSpell):
- For `CastSpell` and `TurnFaceUp`: set `paymentStrategy: { type: 'Explicit', manaAbilitiesToActivate: selectedSources }`.
- For `ActivateAbility`, `CycleCard`, `TypecycleCard`: same — set `paymentStrategy` on the action object.

Then continue with the normal cast flow (targeting, X selection, convoke, delve, sacrifice, etc.) by dispatching to
the appropriate next step.

### Phase 7: Client — GameCard + GameBoard wiring

**File: `web-client/src/components/game/card/GameCard.tsx`**

Already done: mana selection mode highlighting + click handling (parallel to retap).

**File: `web-client/src/components/game/GameBoard.tsx`**

Already done: mana selection controls (progress indicator, Confirm/Cancel), pass button hidden during mode.

## File Change Summary

| File | Change |
|------|--------|
| `rules-engine/.../core/GameAction.kt` | Add `paymentStrategy` to `ActivateAbility`, `CycleCard`, `TypecycleCard` |
| `rules-engine/.../handlers/actions/ability/ActivateAbilityHandler.kt` | Support `Explicit` via `excludeSources` |
| `rules-engine/.../handlers/actions/ability/CycleCardHandler.kt` | Support `Explicit` via `excludeSources` |
| `rules-engine/.../handlers/actions/spell/CastSpellHandler.kt` | **Already done** |
| `game-server/.../protocol/ServerMessage.kt` | **Already done** |
| `game-server/.../legalactions/LegalActionsCalculator.kt` | Extend to attach sources to all mana-costing actions |
| `web-client/src/types/messages.ts` | **Already done** |
| `web-client/src/types/actions.ts` | Add `paymentStrategy` to action types |
| `web-client/src/store/slices/types.ts` | **Already done** |
| `web-client/src/store/slices/uiSlice.ts` | Update `confirmManaSelection` for all action types |
| `web-client/src/components/ui/ActionMenu.tsx` | Replace separate button with inline icon |
| `web-client/src/components/game/card/GameCard.tsx` | **Already done** |
| `web-client/src/components/game/GameBoard.tsx` | **Already done** |

## What's Already Done (current branch)

The current branch has partial implementation:
- Engine: `CastSpellHandler.explicitPay` fixed + `validatePayment` for Explicit
- Server: `availableManaSources` field on `LegalActionInfo`, computed + attached to CastSpell actions
- Client: `ManaSelectionState` type, uiSlice state + actions, GameCard highlighting, GameBoard controls
- Client: ActionMenu has a separate "Choose Mana" button (needs to be replaced with inline icon)

## Remaining Work

1. Add `paymentStrategy` to `ActivateAbility`, `CycleCard`, `TypecycleCard` in `GameAction.kt`
2. Support `Explicit` in `ActivateAbilityHandler` and `CycleCardHandler` (pass `excludeSources`)
3. Server: attach `availableManaSources` to ActivateAbility/Cycle/TurnFaceUp actions too
4. Client: replace separate "Choose Mana" button with inline icon per action
5. Client: update `confirmManaSelection` to handle ActivateAbility/Cycle/TypecycleCard action types
6. Client: add `paymentStrategy` to TS action type definitions for the new action types

## Verification

1. `just build` — verify engine + server compile
2. `cd web-client && npm run typecheck` — verify client types
3. Manual: click spell → see land icon next to "Cast" → click icon → select sources → confirm → spell cast
4. Manual: click card with cycling → see land icon next to "Cycle" → same flow
5. Manual: click permanent with activated ability → see land icon → same flow
6. Manual: drag to cast still auto-taps as before
7. Manual: verify mana progress indicator matches retap behavior
