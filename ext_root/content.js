// Limits scoring to the first ten Google result links.
const MAX_RESULTS = 10;

// Tracks the saved toggle and the delayed result check.
let scoringEnabled = false;
let analysisTimer;

// Converts a Google redirect link into the real destination URL.
function getDestinationUrl(href) {
    const url = new URL(href);
    if (url.pathname === "/url") {
        return url.searchParams.get("q") || url.searchParams.get("url") || "";
    }
    return url.href;
}

// Chooses the short label and color for a score.
function getRiskLevel(score) {
    if (score <= 6) {
        return { label: "Lower risk", className: "netscope-risk-low" };
    }
    if (score <= 13) {
        return { label: "Caution", className: "netscope-risk-medium" };
    }
    return { label: "Higher risk", className: "netscope-risk-high" };
}

// Adds a scored result box below its link.
function showSafetyBox(entry, score, flags) {
    if (entry.link.dataset.netscopeScoreAdded !== "pending") {
        return;
    }
    const level = getRiskLevel(score);
    const box = document.createElement("div");
    box.className = `netscope-safety-box ${level.className}`;
    box.textContent = `${level.label} - ${score}/20`;
    box.title = flags;
    box.setAttribute("role", "status");
    box.setAttribute("aria-label", `${level.label}, ${score} out of 20 points. ${flags}`);
    entry.link.dataset.netscopeScoreAdded = "true";
    entry.link.insertAdjacentElement("afterend", box);
}

// Shows a visible message when Java cannot return a score.
function showErrorBox(entry, message) {
    if (entry.link.dataset.netscopeScoreAdded !== "pending") {
        return;
    }
    const box = document.createElement("div");
    box.className = "netscope-safety-box netscope-risk-error";
    box.textContent = "Score unavailable";
    box.title = message;
    box.setAttribute("role", "status");
    entry.link.dataset.netscopeScoreAdded = "true";
    entry.link.insertAdjacentElement("afterend", box);
}

// Gets the first ten real website links from Google's result headings.
function getResultEntries() {
    return Array.from(document.querySelectorAll("#search h3, #rso h3, .MjjYud h3, .g h3"))
        .map((heading) => {
            const link = heading.closest("a");
            return link ? { link, url: getDestinationUrl(link.href) } : null;
        })
        .filter((entry) => entry && /^https?:\/\//i.test(entry.url))
        .slice(0, MAX_RESULTS);
}

// Reads each score line returned by the Java server.
function parseScores(responseText) {
    return responseText.split(/\r?\n/).map((line) => {
        const parts = line.split(" | ");
        return {
            url: parts[0],
            score: Number.parseInt(parts[1], 10),
            flags: parts[2] || ""
        };
    });
}

// Sends unscored links to Java and adds their returned score boxes.
function processResults() {
    if (!scoringEnabled) {
        return;
    }

    const entries = getResultEntries()
        .filter((entry) => !entry.link.dataset.netscopeScoreAdded);
    if (entries.length === 0) {
        return;
    }

    entries.forEach((entry) => {
        entry.link.dataset.netscopeScoreAdded = "pending";
    });

    chrome.runtime.sendMessage(
        { type: "queueUrls", urls: entries.map((entry) => entry.url) },
        (response) => {
            if (!scoringEnabled) {
                return;
            }

            if (chrome.runtime.lastError || !response || !response.ok
                    || typeof response.results !== "string") {
                const message = chrome.runtime.lastError
                    ? chrome.runtime.lastError.message
                    : response && response.error ? response.error : "Java server is unavailable";
                entries.forEach((entry) => showErrorBox(entry, message));
                console.error("NetScope could not score the search results:", message);
                return;
            }

            const scores = parseScores(response.results);
            entries.forEach((entry) => {
                const result = scores.find((item) => item.url === entry.url);
                if (!result || Number.isNaN(result.score)) {
                    showErrorBox(entry, "No score was returned for this URL");
                    return;
                }
                showSafetyBox(entry, result.score, result.flags);
            });
        }
    );
}

// Removes score boxes when the user turns scoring off.
function clearSafetyBoxes() {
    document.querySelectorAll(".netscope-safety-box").forEach((box) => box.remove());
    document.querySelectorAll("[data-netscope-score-added]").forEach((link) => {
        delete link.dataset.netscopeScoreAdded;
    });
}

// Waits briefly so Google can finish adding its result links.
function scheduleAnalysis() {
    if (!scoringEnabled) {
        return;
    }
    clearTimeout(analysisTimer);
    analysisTimer = setTimeout(processResults, 200);
}

// Applies the saved setting to the current search page.
function setScoringEnabled(enabled) {
    scoringEnabled = enabled;
    if (!enabled) {
        clearTimeout(analysisTimer);
        clearSafetyBoxes();
        return;
    }
    scheduleAnalysis();
}

// Notices Google results that appear after the page first loads.
new MutationObserver(scheduleAnalysis).observe(document.documentElement, {
    childList: true,
    subtree: true
});

// Updates the current page when the popup toggle changes.
chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName === "local" && changes.enabled) {
        setScoringEnabled(changes.enabled.newValue !== false);
    }
});

// Loads the saved setting before scoring any search results.
chrome.storage.local.get({ enabled: true }, (settings) => {
    if (chrome.runtime.lastError) {
        console.error("NetScope could not load its setting:", chrome.runtime.lastError.message);
        return;
    }
    setScoringEnabled(settings.enabled !== false);
});
