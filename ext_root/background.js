// Connects the browser extension to its local Java server.
const JAVA_QUEUE_URL = "http://127.0.0.1:8766/urls";
const JAVA_ANALYZE_URL = "http://127.0.0.1:8766/analyze";

// Gives a helpful message when the local Java server cannot be reached.
function fetchJava(url, options) {
    return fetch(url, options).catch((error) => {
        throw new Error(`Could not reach Java. Restart start-queue.bat. ${error.message}`);
    });
}

// Saves the first ten URLs, then asks Java to score them.
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || message.type !== "queueUrls" || !Array.isArray(message.urls)) {
        return;
    }

    fetchJava(JAVA_QUEUE_URL, {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: message.urls.slice(0, 10).join("\n")
    })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`Java queue returned HTTP ${response.status}`);
            }
            return fetchJava(JAVA_ANALYZE_URL);
        })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`Java scoring returned HTTP ${response.status}`);
            }
            return response.text();
        })
        .then((results) => sendResponse({ ok: true, results }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));

    return true;
});
