// Connects the extension to the Java server on this computer.
const JAVA_QUEUE_URL = "http://127.0.0.1:8766/urls";

// Forwards the first ten URLs to the local Java server.
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || message.type !== "queueUrls" || !Array.isArray(message.urls)) {
        return;
    }

    fetch(JAVA_QUEUE_URL, {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: message.urls.slice(0, 10).join("\n")
    })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`Java queue returned HTTP ${response.status}`);
            }
            sendResponse({ ok: true });
        })
        .catch((error) => sendResponse({ ok: false, error: error.message }));

    return true;
});
