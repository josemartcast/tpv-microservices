# TPV QA Multi-Terminal Checklist

Automatizacion disponible:
- Smoke E2E PDA: `scripts/pda-e2e-smoke.ps1` (ver `docs/pda-e2e-smoke.md`)

## Goal
Validate lock consistency and conflict handling when two terminals operate at the same time.

## Preconditions
- Backend running in real mode (`gateway`, `auth-service`, `pos-service`) with shared MySQL.
- Two TPV instances running with different terminal IDs (for example `T-A` and `T-B`).
- Topbar shows `MODE REAL` in both windows.

## Smoke Checks
1. Confirm terminal labels are different in topbar (`user | T-A` and `user | T-B`).
2. Confirm both windows show `MODE REAL`.
3. Confirm both windows can refresh the table map without errors.

## Test Cases
1. Lock collision:
   - `T-A` opens table `N`.
   - `T-B` tries to open the same table `N`.
   - Expected: `T-B` is blocked with lock conflict message.

2. Heartbeat stability:
   - Keep `T-A` inside table `N` for at least 2 minutes.
   - Expected: lock remains active and no forced navigation occurs.

3. Lock recovery after manual release:
   - While `T-A` is inside table `N`, use Settings -> `Liberar mis bloqueos`.
   - Wait one heartbeat cycle (~20s).
   - Expected: lock is reacquired or user gets controlled fallback message.

4. Full payment unlock:
   - `T-A` fully pays table `N`.
   - Return to salon.
   - Expected: table is free/available in both terminals.

5. Move table:
   - `T-A` moves ticket from table `N` to table `M`.
   - Expected: old table lock is released, new table lock is owned by `T-A`.

6. Empty ticket back:
   - Open a free table, add no products, press back.
   - Expected: table remains free and unlocked.

7. Auth edge case:
   - Force token expiration (or logout/relogin mismatch) during lock actions.
   - Expected: clear auth message and no UI crash.

## Pass Criteria
- No simultaneous edit on the same table.
- No zombie locks after payment/cancel/back/move.
- Same table state seen from both terminals after refresh.

## Regression Notes
- If behavior is inconsistent, check first:
  - both instances are `MODE REAL`;
  - terminal IDs are unique;
  - backend audit events include `TABLE_LOCK` conflicts (`status=FAILED`, `message` contains locked terminal).
