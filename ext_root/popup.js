const toggle = document.getElementById("toggle");
const status = document.getElementById("status");

// Updates the popup to match the saved on/off setting.
function showState(enabled) {
    toggle.setAttribute("aria-pressed", String(enabled));
    toggle.setAttribute("aria-label", `Turn URL scoring ${enabled ? "off" : "on"}`);
    toggle.textContent = enabled ? "ON" : "OFF";
    status.textContent = enabled ? "Scoring is on" : "Scoring is off";
    toggle.disabled = false;
}

// Loads the saved setting when the popup opens.
chrome.storage.local.get({ enabled: true }, (settings) => {
    if (chrome.runtime.lastError) {
        status.textContent = "Could not load setting";
        console.error("Could not load the NetScope setting:", chrome.runtime.lastError.message);
        return;
    }
    showState(settings.enabled !== false);
});

// Saves the new setting when the user clicks the toggle.
toggle.addEventListener("click", () => {
    const enabled = toggle.getAttribute("aria-pressed") !== "true";
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
