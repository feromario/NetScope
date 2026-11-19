const ANALYZER_URL = "http://127.0.0.1:8765";

async function analyzeUrls(urls) {
    const response = await fetch(`${ANALYZER_URL}/analyze`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ urls })
    });

    if (!response.ok) {
        throw new Error(`Analyzer returned HTTP ${response.status}`);
    }
    return response.json();
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message.type === "analyzeUrls") {
        analyzeUrls(Array.isArray(message.urls) ? message.urls : [])
            .then(sendResponse)
            .catch((error) => sendResponse({
                ok: false,
                error: error.message || "Unable to reach the NetScope analyzer"
            }));
        return true;
    }

    if (message.type === "analyzerHealth") {
        fetch(`${ANALYZER_URL}/health`)
            .then((response) => response.ok ? response.json() : Promise.reject(new Error("Analyzer unavailable")))
            .then(sendResponse)
            .catch((error) => sendResponse({ ok: false, error: error.message }));
        return true;
    }
});