# Bug Report: Back Navigation Animation Issues in Subscreens

**Status**: OPEN — Fix attempted, issue persists
**Branch**: `main` (C branch)
**Date**: 2026-10-02

---

## Problem Statement

After migrating all 10 subscreens from `LargeFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior` to `TopAppBar` + `pinnedScrollBehavior`, **the exact same bugs persist**:

1. **Back button (top-left arrow)**: No exit animation — screen dismisses instantly
2. **Back gesture (after screen open >1-2s)**: ~1s lag, then animates
3. **Expressive blur**: Unrenders during transition → transparent layer instead of blur

---

## What Was Changed (Did Not Fix It)

**Files modified** (all in `manager/app/src/main/java/zx/azenith/ui/subscreens/`):
- `FpsGoSettingsScreen.kt`
- `GovSettingsScreen.kt`
- `BypassChargeScreen.kt`
- `BypassCheckScreen.kt`
- `PreferencedTweakScreen.kt`
- `FasSettingsScreen.kt`
- `ColorSchemeScreen.kt`
- `AppSettingsScreen.kt`
- `AboutScreen.kt`
- `CustomThemeScreen.kt`

**Pattern applied**:
```kotlin
// Before
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = { LargeFlexibleTopAppBar(scrollBehavior, onBack = { navController.popBackStack() }) }
)

// After
Scaffold(
    modifier = Modifier.nestedScroll(TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()).nestedScrollConnection),
    topBar = { TopAppBar(onBack = { coroutineScope.launch { navController.popBackStack() } }) }
)
```

**Build**: `./gradlew assembleDebug` — SUCCESS

---

## Root Cause Analysis (Why the Fix Didn't Work)

### 1. Navigation Compose Transition Specs (MainActivity.kt:426-499)

The `NavHost` defines transitions at the **destination level**, not per-screen:

```kotlin
composable(
    route = "fpsgoscreen",
    enterTransition = { slideInHorizontally(initialOffsetX = { it.width }) + fadeIn(tween(500, Easing.EmphasizedAccelerate)) },
    exitTransition = { slideOutHorizontally(targetOffsetX = { it.width }) + fadeOut(tween(500, Easing.EmphasizedAccelerate)) },
    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it.width }) + fadeIn(tween(250, Easing.EmphasizedAccelerate)) },
    popExitTransition = { slideOutHorizontally(targetOffsetX = { it.width }) + fadeOut(tween(250, Easing.EmphasizedAccelerate)) }
)
```

**Critical**: `popExitTransition` runs **only if the destination stays in composition** during the animation. If `popBackStack()` removes it immediately, no animation.

### 2. `coroutineScope.launch { navController.popBackStack() }` Is Not Enough

The coroutine defers the pop by **one frame**, but Navigation Compose may already have decided the destination is "gone" if:
- The back handler runs during a recomposition
- The scaffold content is already being disposed

**Evidence**: System back gesture works (it uses Navigation's default `OnBackPressedDispatcher` which properly coordinates with transitions). The TopAppBar back button bypasses this.

### 3. The Real Issue: `BackHandler` vs TopAppBar `onBack`

**System back gesture** → goes through `BackHandler` / `OnBackPressedDispatcher` → Navigation handles transition coordination.

**TopAppBar back button** → calls `onBack` lambda directly → `popBackStack()` → no transition coordination.

**Fix needed**: The TopAppBar back button must trigger the **same pathway** as system back, not call `popBackStack()` directly.

---

## Correct Fix Approach

### Option A: Use `BackHandler` in Subscreen (Recommended)

```kotlin
@Composable
fun FpsGoSettingsScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    
    // Handle system back AND TopAppBar back the same way
    BackHandler(enabled = true) {
        coroutineScope.launch { navController.popBackStack() }
    }
    
    Scaffold(
        topBar = { 
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { 
                        // Trigger the SAME back handler pathway
                        // Not popBackStack() directly!
                        // Use a shared callback or OnBackPressedDispatcher
                    }) { ... }
                }
            )
        }
    ) { ... }
}
```

### Option B: Use `OnBackPressedDispatcher` Directly

```kotlin
val backDispatcher = remember { OnBackPressedDispatcher(false) }
val backCallback = remember { 
    object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            coroutineScope.launch { navController.popBackStack() }
        }
    }
}
DisposableEffect(backDispatcher) {
    backDispatcher.addCallback(backCallback)
    onDispose { backCallback.remove() }
}

// In TopAppBar:
navigationIcon = {
    IconButton(onClick = { backCallback.handleOnBackPressed() }) { ... }
}
```

### Option C: Navigation Compose's `popBackStack()` with `inclusive = false` + Proper Timing

The current code already uses `coroutineScope.launch`. The issue may be that **the scaffold/content is already exiting composition** before the launch executes. Need to ensure the destination stays composed until `popExitTransition` completes.

---

## Additional Observations

### Expressive Blur Issue
- `ExpressiveBottomBar` / `ExpressiveTopAppBar` use `ExpressiveRipple` / blur surfaces
- During transition, the blur surface is torn down before animation completes
- May need `AnimatedContent` with shared element transition or `Modifier.graphicsLayer` to preserve blur layer

### 1s Delay on Back Gesture (After Screen Open >1-2s)
- Likely **focus/touch slop** or **gesture detector timeout** in `NavigationCompose`
- Or: `NestedScrollConnection` from `pinnedScrollBehavior` still intercepting
- Check: `Modifier.nestedScroll(TopAppBarDefaults.pinnedScrollBehavior(...).nestedScrollConnection)` — does this still consume back gesture?

---

## Test Checklist for Next Agent

- [ ] System back gesture: smooth exit animation ✓ (already works)
- [ ] TopAppBar back button: smooth exit animation ✗ (instant dismiss)
- [ ] Mid-animation interrupt (gesture pull-back): smooth close ✓ (already works)
- [ ] Expressive blur preserved during transition ✗ (transparent layer)
- [ ] Back gesture after 2s open: no 1s lag ✗ (lags then animates)

---

## Files to Investigate

1. **MainActivity.kt:426-499** — NavHost transition specs
2. **Any subscreen** — TopAppBar back button handler
3. **compose-animations skill** — for `AnimatedContent` interrupt handling
4. **Navigation Compose source** — `popExitTransition` execution conditions

---

## Suggested Next Steps

1. **Replace direct `popBackStack()` with `OnBackPressedDispatcher` callback** so TopAppBar back uses same pathway as system back
2. **Verify `popExitTransition` actually runs** — add logging in `MainActivity` composable transitions
3. **Check if `pinnedScrollBehavior.nestedScrollConnection` still intercepts back gesture** — may need `Modifier.nestedScroll(InterceptAllNestedScroll)` or disable nested scroll for back gesture
4. **For blur**: wrap scaffold content in `AnimatedContent` with shared transition key, or use `Modifier.graphicsLayer { alpha = 1f }` to prevent layer tear-down

---

## Notes

- The `compose-animations` skill was pruned in this session — reload with `skill_view(name='compose-animations')` for transition expertise
- All subscreens follow identical pattern — fix one, apply to all
- Do NOT revert to `LargeFlexibleTopAppBar` — that was correctly identified as the cause of gesture interception