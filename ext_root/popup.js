const toggle = document.getElementById("toggle");
const status = document.getElementById("status");
const topResultsList = document.getElementById("top-results-list");
const topResultsEmpty = document.getElementById("top-results-empty");

// Updates the popup to match the saved on/off setting.
function showState(enabled) {
    toggle.setAttribute("aria-checked", String(enabled));
    status.textContent = enabled ? "Scoring is on" : "Scoring is off";
    toggle.disabled = false;
}

// Renders the saved URLs as safe, clickable links in the popup.
function showTopResults(results) {
    topResultsList.replaceChildren();

    if (!Array.isArray(results) || results.length === 0) {
        topResultsEmpty.textContent = "No scored URLs yet.";
        topResultsEmpty.hidden = false;
        topResultsList.hidden = true;
        return;
    }

    results.slice(0, 3).forEach((result) => {
        let url;
        if (!result || typeof result.url !== "string" || !Number.isFinite(result.score)) {
            return;
        }
        try {
            url = new URL(result.url);
        } catch (_error) {
            return;
        }
        if (url.protocol !== "http:" && url.protocol !== "https:") {
            return;
        }

        const item = document.createElement("li");
        const link = document.createElement("a");
        link.href = url.href;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.textContent = result.url;

        const score = document.createElement("span");
        score.className = "result-score";
        score.textContent = ` - ${result.score}/20`;

        item.append(link, score);
        topResultsList.appendChild(item);
    });

    const hasResults = topResultsList.childElementCount > 0;
    topResultsEmpty.textContent = hasResults ? "" : "No scored URLs yet.";
    topResultsEmpty.hidden = hasResults;
    topResultsList.hidden = !hasResults;
}

// Loads the saved setting and latest scored URLs when the popup opens.
chrome.storage.local.get({ enabled: true, topResults: [] }, (settings) => {
    if (chrome.runtime.lastError) {
        status.textContent = "Could not load setting";
        console.error("Could not load the NetScope setting:", chrome.runtime.lastError.message);
        return;
    }
    showState(settings.enabled !== false);
    showTopResults(settings.topResults);
});

// Refreshes the list if scoring finishes while the popup is open.
chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName === "local" && changes.topResults) {
        showTopResults(changes.topResults.newValue);
    }
});

// Saves the new setting when the user clicks the toggle.
toggle.addEventListener("click", () => {
    const enabled = toggle.getAttribute("aria-checked") !== "true";
    toggle.disabled = true;
    status.textContent = "Saving...";

    chrome.storage.local.set({ enabled }, () => {
        if (chrome.runtime.lastError) {
            status.textContent = "Could not save setting";
            toggle.disabled = false;
            console.error("Could not save the NetScope setting:", chrome.runtime.lastError.message);
            return;
        }
        showState(enabled);
    });
});
