const toggle = document.getElementById("toggle");
const status = document.getElementById("status");

// Updates the button and message to match the saved setting.
function showState(enabled) {
    toggle.setAttribute("aria-pressed", String(enabled));
    toggle.textContent = enabled ? "Turn off" : "Turn on";
    status.textContent = enabled ? "NetScope is on" : "NetScope is off";
    toggle.disabled = false;
}

// Loads the saved setting when the popup opens.
chrome.storage.local.get({ enabled: true }, (settings) => {
    if (chrome.runtime.lastError) {
        status.textContent = "Could not load setting";
        return;
    }

    showState(settings.enabled !== false);
});

// Saves the new setting when the user clicks the button.
toggle.addEventListener("click", () => {
    const enabled = toggle.getAttribute("aria-pressed") !== "true";
    toggle.disabled = true;
    status.textContent = "Saving...";

    chrome.storage.local.set({ enabled }, () => {
        if (chrome.runtime.lastError) {
            status.textContent = "Could not save setting";
            toggle.disabled = false;
            return;
        }

        showState(enabled);
    });
});
