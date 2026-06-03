(function () {
    'use strict';

    // 1. Intercept fetch to capture submitted URL for retry storage
    var _origFetch = window.fetch.bind(window);
    window.fetch = function (resource, options) {
        try {
            if (options && options.body) {
                var body = JSON.parse(options.body);
                if (body && typeof body.url === 'string' && body.url.length > 0) {
                    window.CobaltBridge && window.CobaltBridge.onUrlSubmitted(body.url);
                }
            }
        } catch (e) { /* ignore */ }
        return _origFetch(resource, options);
    };

    // 2. Inject URL into cobalt's input field (Svelte requires native setter trick)
    window._cobaltInjectUrl = function (url, audioOnly) {
        try {
            var input = document.querySelector('input[type="text"]') ||
                        document.querySelector('input[type="url"]') ||
                        document.querySelector('input:not([type])');
            if (!input) throw new Error('input not found');

            var nativeSetter = Object.getOwnPropertyDescriptor(
                window.HTMLInputElement.prototype, 'value'
            ).set;
            nativeSetter.call(input, url);
            input.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
            input.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));

            if (audioOnly) {
                try {
                    var buttons = document.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                        if (buttons[i].textContent.trim().toLowerCase() === 'audio') {
                            buttons[i].click();
                            break;
                        }
                    }
                } catch (e2) { /* non-fatal */ }
            }

            setTimeout(function () {
                try {
                    var submitBtn = document.querySelector('button[type="submit"]') ||
                        document.querySelector('form button');
                    if (submitBtn) submitBtn.click();
                } catch (e3) { /* non-fatal */ }
            }, 150);

            window.CobaltBridge && window.CobaltBridge.onInjectComplete('true');
        } catch (e) {
            window.CobaltBridge && window.CobaltBridge.onInjectComplete('false');
        }
    };

    // 3. Blob download: read as ArrayBuffer, send to native in 1MB chunks
    window._cobaltDownloadBlob = function (blobUrl, filename, mimeType) {
        fetch(blobUrl)
            .then(function (r) { return r.arrayBuffer(); })
            .then(function (buffer) {
                var bytes = new Uint8Array(buffer);
                var CHUNK = 1024 * 1024; // 1MB
                var total = bytes.length;

                window.CobaltBridge.onBlobStart(filename, mimeType, total);

                var offset = 0;
                function sendChunk() {
                    if (offset >= total) {
                        window.CobaltBridge.onBlobComplete();
                        return;
                    }
                    var end = Math.min(offset + CHUNK, total);
                    var slice = bytes.subarray(offset, end);
                    var binary = '';
                    for (var i = 0; i < slice.length; i++) {
                        binary += String.fromCharCode(slice[i]);
                    }
                    window.CobaltBridge.onBlobChunk(btoa(binary));
                    offset = end;
                    setTimeout(sendChunk, 0);
                }
                sendChunk();
            })
            .catch(function (e) {
                window.CobaltBridge.onBlobError(e.message || 'blob read failed');
            });
    };
})();
