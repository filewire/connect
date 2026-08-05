/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.webkit.WebView

/**
 * Injects CSS/JS so Element Call's draggable self-view (PiP tile) keeps receiving
 * touch events inside Android WebView.
 *
 * Element Call marks PiP tiles with a `_draggable_*` class and uses @use-gesture/react,
 * but does not set `touch-action: none`. On mobile WebView that cancels the pointer
 * mid-drag (tile moves slightly, then snaps back).
 *
 * Upstream: https://github.com/element-hq/element-call/issues/3548
 */
internal object WebViewCallTouchFix {
    // language=JavaScript
    private val TOUCH_ACTION_FIX_SCRIPT = """
        (function() {
            const STYLE_ID = 'connect-ec-touch-fix';
            const CSS = `
              [class*="_draggable_"],
              [data-block-alignment],
              [data-inline-alignment] {
                touch-action: none !important;
                -webkit-user-select: none !important;
                user-select: none !important;
              }
            `;
            function ensureStyle() {
                const root = document.head || document.documentElement;
                if (!root) return;
                let style = document.getElementById(STYLE_ID);
                if (!style) {
                    style = document.createElement('style');
                    style.id = STYLE_ID;
                    root.appendChild(style);
                }
                style.textContent = CSS;
            }
            function applyInline(el) {
                if (!el || el.nodeType !== 1 || !el.matches) return;
                if (el.matches('[class*="_draggable_"],[data-block-alignment],[data-inline-alignment]')) {
                    el.style.setProperty('touch-action', 'none', 'important');
                }
            }
            function scan(root) {
                applyInline(root);
                if (root && root.querySelectorAll) {
                    root.querySelectorAll('[class*="_draggable_"],[data-block-alignment],[data-inline-alignment]')
                        .forEach(applyInline);
                }
            }
            ensureStyle();
            scan(document);

            if (!window.__connectEcTouchObs) {
                window.__connectEcTouchObs = new MutationObserver(function(mutations) {
                    ensureStyle();
                    for (var i = 0; i < mutations.length; i++) {
                        var m = mutations[i];
                        if (m.type === 'attributes' && m.target) {
                            applyInline(m.target);
                        }
                        var nodes = m.addedNodes;
                        for (var j = 0; j < nodes.length; j++) {
                            scan(nodes[j]);
                        }
                    }
                });
                window.__connectEcTouchObs.observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class']
                });
            }

            if (!window.__connectEcTouchMove) {
                window.__connectEcTouchMove = true;
                document.addEventListener('touchmove', function(e) {
                    var t = e.target;
                    if (t && t.closest && t.closest('[class*="_draggable_"],[data-block-alignment]')) {
                        e.preventDefault();
                    }
                }, { passive: false, capture: true });
            }
        })();
    """.trimIndent()

    fun apply(webView: WebView) {
        webView.evaluateJavascript(TOUCH_ACTION_FIX_SCRIPT, null)
    }
}
