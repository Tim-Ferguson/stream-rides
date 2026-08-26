package com.pelotonhack.ridestarter;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TvRemoteController implements TvRemoteOverlay.Listener {
    private static final String TAG = "SaroTvRemote";
    private static final long FOREGROUND_POLL_MS = 500;
    private static final long HIDDEN_POLL_MS = 2000;
    private static final long FAILURE_TOAST_COOLDOWN_MS = 2500;
    private static final int MAX_TREE_DEPTH = 64;
    private static final int MAX_TREE_NODES = 512;
    private static final int MAX_CANDIDATES = 128;
    private static final long TREE_BUDGET_MS = 75;

    private final AccessibilityService service;
    private final Handler handler;
    private final TvRemoteOverlay overlay;
    private final TvRemoteSelection selection = new TvRemoteSelection();
    private final TvRemoteVariantResolver variants;
    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            updateOverlayForActiveWindow();
            if (running) {
                handler.postDelayed(this,
                        overlay.isVisible() ? FOREGROUND_POLL_MS : HIDDEN_POLL_MS);
            }
        }
    };

    private long lastFailureToastAt;
    private int unsupportedPolls;
    private int visibleProviderWindowId = -1;
    private String activeProviderPackage;
    private boolean running;

    TvRemoteController(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
        this.overlay = new TvRemoteOverlay(service, this);
        this.variants = new TvRemoteVariantResolver(service);
    }

    void start() {
        running = true;
        handler.removeCallbacks(foregroundPoll);
        handler.post(foregroundPoll);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(foregroundPoll);
        clearSelection();
        activeProviderPackage = null;
        visibleProviderWindowId = -1;
        overlay.hide();
    }

    void interrupt() {
        clearSelection();
        activeProviderPackage = null;
        visibleProviderWindowId = -1;
        overlay.hide();
    }

    void reloadOverlayPosition() {
        clearSelection();
        overlay.reloadPosition();
    }

    void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            clearSelection();
            CharSequence packageName = event.getPackageName();
            if (!TvRemotePackages.supports(packageName)) {
                activeProviderPackage = null;
                hideImmediately();
                return;
            }
            activeProviderPackage = packageName.toString();
            if (!variants.isTvVariant(activeProviderPackage)) {
                hideImmediately();
                return;
            }
            updateOverlayForActiveWindow();
        }
    }

    @Override
    public void onDirection(int direction) {
        AccessibilityNodeInfo root = null;
        try {
            clearSelection();
            root = supportedRoot();
            if (!isVisibleProviderRoot(root)) {
                clearSelection();
                unavailable();
                return;
            }
            String packageName = root.getPackageName().toString();
            boolean handled;
            if (TvRemotePackages.usesVirtualDpad(root.getPackageName())) {
                handled = dispatchVirtualDpad(root, direction);
                if (handled) {
                    selection.arm(packageName, root.getWindowId(), null, true,
                            android.os.SystemClock.elapsedRealtime());
                }
            } else {
                String targetKey = moveSemanticFocus(root, direction);
                handled = targetKey != null;
                if (handled) {
                    selection.arm(packageName, root.getWindowId(), targetKey, false,
                            android.os.SystemClock.elapsedRealtime());
                }
            }
            if (!handled) {
                unavailable();
            }
        } catch (RuntimeException exception) {
            reportFailure("direction", exception);
        } finally {
            recycle(root);
        }
    }

    @Override
    public void onSelect() {
        AccessibilityNodeInfo root = null;
        try {
            root = supportedRoot();
            if (!isVisibleProviderRoot(root)) {
                clearSelection();
                unavailable();
                return;
            }
            boolean handled;
            String packageName = root.getPackageName().toString();
            TvRemoteSelection.Result selected = selection.consume(packageName,
                    root.getWindowId(),
                    android.os.SystemClock.elapsedRealtime());
            if (selected.decision == TvRemoteSelection.Decision.CONFIRM_REQUIRED) {
                Toast.makeText(service, "Press OK again to activate the focused item",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (selected.decision != TvRemoteSelection.Decision.ALLOWED) {
                unavailable();
                return;
            }
            if (TvRemotePackages.usesVirtualDpad(root.getPackageName())) {
                handled = dispatchVirtualDpad(root, -1);
            } else {
                handled = clickSemanticFocus(root, selected.targetKey);
            }
            if (!handled) {
                unavailable();
            }
        } catch (RuntimeException exception) {
            reportFailure("select", exception);
        } finally {
            recycle(root);
        }
    }

    @Override
    public void onBack() {
        AccessibilityNodeInfo root = null;
        try {
            clearSelection();
            root = supportedRoot();
            if (!isVisibleProviderRoot(root)) {
                unavailable();
                return;
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                unavailable();
            }
        } catch (RuntimeException exception) {
            reportFailure("back", exception);
        } finally {
            recycle(root);
        }
    }

    @Override
    public void onSaroHome() {
        clearSelection();
        activeProviderPackage = null;
        visibleProviderWindowId = -1;
        overlay.hide();
        try {
            MediaLauncher.openHub(service);
        } catch (RuntimeException exception) {
            reportFailure("open SARO Home", exception);
        }
    }

    @Override
    public void onRemoteModeChanged() {
        clearSelection();
    }

    @Override
    public void onOverlayUnavailable() {
        clearSelection();
        visibleProviderWindowId = -1;
    }

    private void updateOverlayForActiveWindow() {
        AccessibilityNodeInfo root = null;
        try {
            if (MainActivity.isForeground() || GameActivity.isForeground()) {
                unsupportedPolls = 0;
                clearSelection();
                activeProviderPackage = null;
                visibleProviderWindowId = -1;
                overlay.hide();
                return;
            }
            if (activeProviderPackage == null
                    || !variants.isTvVariant(activeProviderPackage)) {
                hideImmediately();
                return;
            }
            root = supportedRoot();
            if (root == null) {
                hideAfterStableUnsupportedWindow();
                return;
            }
            if (samePackage(activeProviderPackage, root.getPackageName())) {
                unsupportedPolls = 0;
                if (visibleProviderWindowId >= 0
                        && visibleProviderWindowId != root.getWindowId()) {
                    clearSelection();
                }
                visibleProviderWindowId = root.getWindowId();
                overlay.show();
            } else {
                hideAfterStableUnsupportedWindow();
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Provider window inspection failed", exception);
            hideAfterStableUnsupportedWindow();
        } finally {
            recycle(root);
        }
    }

    private AccessibilityNodeInfo supportedRoot() {
        return findSupportedForegroundWindowRoot();
    }

    private AccessibilityNodeInfo findSupportedForegroundWindowRoot() {
        final String expectedPackage = activeProviderPackage;
        if (expectedPackage == null || !variants.isTvVariant(expectedPackage)) {
            return null;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return null;
        }
        AccessibilityWindowInfo selected = null;
        int selectedRank = Integer.MIN_VALUE;
        int selectedLayer = Integer.MIN_VALUE;
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null) {
                    continue;
                }
                int rank = TvRemoteWindowPolicy.rank(
                        window.isActive(), window.isFocused());
                int layer = window.getLayer();
                if (TvRemoteWindowPolicy.shouldReplace(
                        selectedRank, selectedLayer, rank, layer)) {
                    selected = window;
                    selectedRank = rank;
                    selectedLayer = layer;
                }
            }
            if (selected == null || !TvRemoteWindowPolicy.isSupportedForegroundType(
                    selected.getType())) {
                return null;
            }
            AccessibilityNodeInfo root = selected.getRoot();
            if (root == null) {
                return null;
            }
            if (samePackage(expectedPackage, root.getPackageName())) {
                return root;
            }
            activeProviderPackage = null;
            recycle(root);
            return null;
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                recycle(window);
            }
        }
    }

    private boolean dispatchVirtualDpad(AccessibilityNodeInfo root, int direction) {
        if (direction < 0) {
            return performVirtualAction(root, "DPAD Top Button",
                    AccessibilityNodeInfo.ACTION_CLICK);
        }
        return performVirtualAction(root, virtualDescription(direction),
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
    }

    private boolean performVirtualAction(AccessibilityNodeInfo node, String description,
                                         int action) {
        return performVirtualAction(node, description, action, 0, new TraversalBudget());
    }

    private boolean performVirtualAction(AccessibilityNodeInfo node, String description,
                                         int action, int depth, TraversalBudget budget) {
        if (depth >= MAX_TREE_DEPTH || !budget.enterNode()) {
            return false;
        }
        CharSequence nodeDescription = node.getContentDescription();
        if (nodeDescription != null && description.contentEquals(nodeDescription)) {
            try {
                return node.performAction(action);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (performVirtualAction(child, description, action, depth + 1, budget)) {
                    return true;
                }
            } finally {
                recycle(child);
            }
            if (!budget.canContinue()) {
                break;
            }
        }
        return false;
    }

    private String moveSemanticFocus(AccessibilityNodeInfo root, int direction) {
        AccessibilityNodeInfo focused = null;
        AccessibilityNodeInfo next = null;
        try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            AccessibilityNodeInfo searchFrom = focused == null ? root : focused;
            try {
                next = searchFrom.focusSearch(viewDirection(direction));
            } catch (RuntimeException ignored) {
                next = null;
            }
            if (isUsableTarget(root, next) && focusInput(next)) {
                return refreshAndResolveTargetKey(next);
            }

            Rect sourceRect = new Rect();
            if (focused != null) {
                focused.getBoundsInScreen(sourceRect);
            }
            if (sourceRect.isEmpty()) {
                root.getBoundsInScreen(sourceRect);
                int centerX = sourceRect.centerX();
                int centerY = sourceRect.centerY();
                sourceRect.set(centerX - 1, centerY - 1, centerX + 1, centerY + 1);
            }

            List<NodeCandidate> candidates = new ArrayList<NodeCandidate>();
            try {
                collectCandidates(root, root.getPackageName(), rootBounds(root), candidates,
                        new HashMap<String, Integer>(), 0, new TraversalBudget());
                List<TvFocusNavigator.Bounds> bounds = new ArrayList<TvFocusNavigator.Bounds>();
                for (NodeCandidate candidate : candidates) {
                    bounds.add(candidate.bounds);
                }
                int index = TvFocusNavigator.findNext(toBounds(sourceRect), bounds, direction);
                NodeCandidate candidate = index < 0 ? null : candidates.get(index);
                return candidate != null && focusInput(candidate.node)
                        ? refreshAndResolveTargetKey(candidate.node) : null;
            } finally {
                recycleCandidates(candidates);
            }
        } finally {
            recycle(next);
            recycle(focused);
        }
    }

    private boolean clickSemanticFocus(AccessibilityNodeInfo root, String expectedTargetKey) {
        if (expectedTargetKey == null) {
            return false;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        ResolvedSemanticTarget target = null;
        try {
            if (focused == null || !focused.refresh()) {
                return false;
            }
            target = resolveSemanticTarget(focused);
            if (target == null || !expectedTargetKey.equals(target.key)) {
                return false;
            }
            recycle(target.clickable);
            target = null;
            if (!focused.refresh() || !focused.isFocused()) {
                return false;
            }
            target = resolveSemanticTarget(focused);
            if (target == null || !expectedTargetKey.equals(target.key)
                    || !target.clickable.refresh()) {
                return false;
            }
            return target.clickable.isEnabled() && target.clickable.isClickable()
                    && target.clickableKey.equals(nodeFingerprint(target.clickable))
                    && target.clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            recycle(target == null ? null : target.clickable);
            recycle(focused);
        }
    }

    private static String refreshAndResolveTargetKey(AccessibilityNodeInfo node) {
        if (!node.refresh() || !node.isFocused()) {
            return null;
        }
        ResolvedSemanticTarget target = resolveSemanticTarget(node);
        try {
            return target == null ? null : target.key;
        } finally {
            recycle(target == null ? null : target.clickable);
        }
    }

    private static ResolvedSemanticTarget resolveSemanticTarget(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        while (current != null) {
            try {
                if (current.isEnabled() && current.isClickable()) {
                    String clickableKey = nodeFingerprint(current);
                    return new ResolvedSemanticTarget(
                            TvRemoteTargetKey.target(nodeFingerprint(node), clickableKey),
                            clickableKey, current);
                }
                AccessibilityNodeInfo parent = current.getParent();
                recycle(current);
                current = parent;
            } catch (RuntimeException exception) {
                recycle(current);
                return null;
            }
        }
        return null;
    }

    private void collectCandidates(AccessibilityNodeInfo node, CharSequence packageName,
                                   Rect screenBounds, List<NodeCandidate> result,
                                   Map<String, Integer> candidateIndexes, int depth,
                                   TraversalBudget budget) {
        if (depth >= MAX_TREE_DEPTH || result.size() >= MAX_CANDIDATES
                || !budget.enterNode()) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isVisibleToUser() && node.isEnabled()
                && node.isFocusable()
                && !bounds.isEmpty() && Rect.intersects(bounds, screenBounds)
                && samePackage(packageName, node.getPackageName())) {
            long area = (long) bounds.width() * bounds.height();
            long screenArea = (long) screenBounds.width() * screenBounds.height();
            boolean hasLabel = hasText(node.getText()) || hasText(node.getContentDescription());
            if (area * 4L <= screenArea * 3L || hasLabel) {
                String key = bounds.flattenToString();
                int score = candidateScore(node, hasLabel);
                Integer existingIndex = candidateIndexes.get(key);
                if (existingIndex == null) {
                    candidateIndexes.put(key, result.size());
                    result.add(new NodeCandidate(AccessibilityNodeInfo.obtain(node),
                            toBounds(bounds), score));
                } else if (score > result.get(existingIndex).score) {
                    NodeCandidate previous = result.get(existingIndex);
                    recycle(previous.node);
                    result.set(existingIndex, new NodeCandidate(
                            AccessibilityNodeInfo.obtain(node), toBounds(bounds), score));
                }
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collectCandidates(child, packageName, screenBounds, result,
                        candidateIndexes, depth + 1, budget);
            } finally {
                recycle(child);
            }
            if (result.size() >= MAX_CANDIDATES || !budget.canContinue()) {
                break;
            }
        }
    }

    private static Rect rootBounds(AccessibilityNodeInfo root) {
        Rect result = new Rect();
        root.getBoundsInScreen(result);
        return result;
    }

    private static boolean isUsableTarget(AccessibilityNodeInfo root,
                                          AccessibilityNodeInfo candidate) {
        return candidate != null && candidate.isVisibleToUser() && candidate.isEnabled()
                && candidate.isFocusable()
                && samePackage(root.getPackageName(), candidate.getPackageName());
    }

    private static boolean focusInput(AccessibilityNodeInfo node) {
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void hideAfterStableUnsupportedWindow() {
        unsupportedPolls += 1;
        if (unsupportedPolls >= 4) {
            clearSelection();
            visibleProviderWindowId = -1;
            overlay.hide();
        }
    }

    private void hideImmediately() {
        unsupportedPolls = 0;
        clearSelection();
        visibleProviderWindowId = -1;
        overlay.hide();
    }

    private void unavailable() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastFailureToastAt < FAILURE_TOAST_COOLDOWN_MS) {
            return;
        }
        lastFailureToastAt = now;
        Toast.makeText(service, "TV control is unavailable on this screen",
                Toast.LENGTH_SHORT).show();
    }

    private void reportFailure(String operation, RuntimeException exception) {
        clearSelection();
        Log.w(TAG, "TV remote " + operation + " failed", exception);
        unavailable();
    }

    private void clearSelection() {
        selection.clear();
    }

    private boolean isVisibleProviderRoot(AccessibilityNodeInfo root) {
        return root != null && visibleProviderWindowId >= 0
                && root.getWindowId() == visibleProviderWindowId
                && activeProviderPackage != null
                && variants.isTvVariant(activeProviderPackage)
                && samePackage(activeProviderPackage, root.getPackageName());
    }

    private static String nodeFingerprint(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        CharSequence className = node.getClassName();
        String viewId = node.getViewIdResourceName();
        return TvRemoteTargetKey.node(node.getWindowId(), node.hashCode(),
                bounds.flattenToString(), className == null ? null : className.toString(),
                viewId, node.getActions(), node.isFocusable(), node.isClickable(),
                node.isEnabled(), node.isFocused(), node.isVisibleToUser(), node.getText(),
                node.getContentDescription(), node.getStateDescription());
    }

    private static int candidateScore(AccessibilityNodeInfo node, boolean hasLabel) {
        int score = node.isFocused() ? 16 : 0;
        score += node.isClickable() ? 8 : 0;
        score += hasLabel ? 4 : 0;
        return score;
    }

    private static void recycle(AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }
        try {
            node.recycle();
        } catch (RuntimeException ignored) {
            // A provider may invalidate its tree between discovery and cleanup.
        }
    }

    private static void recycle(AccessibilityWindowInfo window) {
        if (window == null) {
            return;
        }
        try {
            window.recycle();
        } catch (RuntimeException ignored) {
            // Window snapshots can disappear while the provider is transitioning.
        }
    }

    private static String virtualDescription(int direction) {
        switch (direction) {
            case TvFocusNavigator.UP:
                return "DPAD Top Button";
            case TvFocusNavigator.RIGHT:
                return "DPAD Right Button";
            case TvFocusNavigator.DOWN:
                return "DPAD Bottom Button";
            case TvFocusNavigator.LEFT:
                return "DPAD Left Button";
            default:
                return "";
        }
    }

    private static int viewDirection(int direction) {
        switch (direction) {
            case TvFocusNavigator.UP:
                return View.FOCUS_UP;
            case TvFocusNavigator.RIGHT:
                return View.FOCUS_RIGHT;
            case TvFocusNavigator.DOWN:
                return View.FOCUS_DOWN;
            case TvFocusNavigator.LEFT:
                return View.FOCUS_LEFT;
            default:
                return View.FOCUS_FORWARD;
        }
    }

    private static boolean hasText(CharSequence value) {
        return value != null && value.length() > 0;
    }

    private static boolean samePackage(CharSequence expected, CharSequence actual) {
        return expected != null && actual != null && expected.toString().contentEquals(actual);
    }

    private static TvFocusNavigator.Bounds toBounds(Rect rect) {
        return new TvFocusNavigator.Bounds(rect.left, rect.top, rect.right, rect.bottom);
    }

    private static void recycleCandidates(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            recycle(candidate.node);
        }
    }

    private static final class NodeCandidate {
        final AccessibilityNodeInfo node;
        final TvFocusNavigator.Bounds bounds;
        final int score;

        NodeCandidate(AccessibilityNodeInfo node, TvFocusNavigator.Bounds bounds,
                      int score) {
            this.node = node;
            this.bounds = bounds;
            this.score = score;
        }
    }

    private static final class ResolvedSemanticTarget {
        final String key;
        final String clickableKey;
        final AccessibilityNodeInfo clickable;

        ResolvedSemanticTarget(String key, String clickableKey,
                               AccessibilityNodeInfo clickable) {
            this.key = key;
            this.clickableKey = clickableKey;
            this.clickable = clickable;
        }
    }

    private static final class TraversalBudget {
        private final long deadline = android.os.SystemClock.uptimeMillis()
                + TREE_BUDGET_MS;
        private int visitedNodes;

        boolean enterNode() {
            if (!canContinue()) {
                return false;
            }
            visitedNodes += 1;
            return true;
        }

        boolean canContinue() {
            return visitedNodes < MAX_TREE_NODES
                    && android.os.SystemClock.uptimeMillis() <= deadline;
        }
    }
}
