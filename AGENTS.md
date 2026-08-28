# Agent System Directives

## Mandatory Reference Documents
Before making any architectural changes, editing code, or adding features:
1. **MUST READ `BLUEPRINT.md`**: Contains the blueprint for the entire BLE Proximity → Samsung Mode Automation system.
2. **MUST READ `PHASES.md`**: Outlines the strict phase-by-phase development path.

## Architectural Mandates
1. **Separation of Concerns**:
   - `BLE Scanner` / `ProximityEngine` MUST NOT know about Samsung Modes APIs.
   - `SamsungModeController` MUST NOT know about Bluetooth Low Energy.
   - The only link between them is `ProximityAutomationController`.
2. **Preserve Working Samsung Modes POC**:
   - Do NOT rewrite or break the existing One UI 8.0 (`SamsungModeControllerV8`) or One UI 8.5 (`SamsungModeControllerV85`) controller code.
3. **Strict Phase Adherence**:
   - Only implement features corresponding to the active phase in `PHASES.md`.
   - Never skip ahead to unapproved phases without explicit direction.
