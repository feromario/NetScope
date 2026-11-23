// Connects the browser extension to its local Java server.
const JAVA_QUEUE_URL = "http://127.0.0.1:8766/urls";
const JAVA_ANALYZE_URL = "http://127.0.0.1:8766/analyze";
const JAVA_TOP_URL = "http://127.0.0.1:8766/top";

// Gives a helpful message when the local Java server cannot be reached.
function fetchJava(url, options) {
    return fetch(url, options).catch((error) => {
        throw new Error(`Could not reach Java. Restart start-queue.bat. ${error.message}`);
    });
}

// Converts the server's score lines into the small objects shown by the popup.
function parseTopResults(responseText) {
    return responseText.split(/\r?\n/).map((line) => {
        const parts = line.split(" | ");
        const score = Number.parseInt(parts[1], 10);
        if (!parts[0] || !Number.isFinite(score) || !/^https?:\/\//i.test(parts[0])) {
            throw new Error("Java returned an invalid lowest-score result.");
        }
        return { url: parts[0], score };
    });
}

// Saves the latest top results so they remain available when the popup opens.
function saveTopResults(topResults) {
    return new Promise((resolve, reject) => {
        chrome.storage.local.set({ topResults }, () => {
            if (chrome.runtime.lastError) {
                reject(new Error(`Could not save popup results: ${chrome.runtime.lastError.message}`));
                return;
            }
            resolve();
        });
    });
}

// Saves the first ten URLs, then asks Java to score them.
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || message.type !== "queueUrls" || !Array.isArray(message.urls)) {
        return;
    }

    saveTopResults([])
        .then(() => fetchJava(JAVA_QUEUE_URL, {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: message.urls.slice(0, 10).join("\n")
        }))
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
        .then((results) => fetchJava(JAVA_TOP_URL)
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Java top-results request returned HTTP ${response.status}`);
                }
                return response.text();
            })
            .then((topResultsText) => saveTopResults(parseTopResults(topResultsText)))
            .then(() => sendResponse({ ok: true, results })))
        .catch((error) => sendResponse({ ok: false, error: error.message }));

    return true;
});
