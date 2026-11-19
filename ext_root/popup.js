(() => {
    const toggle = document.getElementById("toggle");
    const toggleLabel = document.getElementById("toggle-label");
    const status = document.getElementById("status");

    function renderState(enabled) {
        toggle.setAttribute("aria-pressed", String(enabled));
        toggle.setAttribute("aria-label", `Turn NetScope ${enabled ? "off" : "on"}`);
        toggleLabel.textContent = enabled ? "ON" : "OFF";
        status.textContent = enabled ? "ACTIVE" : "PAUSED";
        status.dataset.enabled = String(enabled);
        delete status.dataset.error;
        toggle.disabled = false;
    }

    function showError(message) {
        status.textContent = message;
        status.dataset.error = "true";
    }

    chrome.storage.local.get({ enabled: true }, (settings) => {
        if (chrome.runtime.lastError) {
            console.error("NetScope could not load its enabled state:", chrome.runtime.lastError.message);
            showError("LOAD FAILED");
            toggle.disabled = true;
            return;
        }

        renderState(settings.enabled !== false);
        chrome.runtime.sendMessage({ type: "analyzerHealth" }, (health) => {
            if (chrome.runtime.lastError || !health || !health.ok) {
                status.textContent = "ANALYZER OFFLINE";
                status.dataset.error = "true";
            }
        });
    });

    toggle.addEventListener("click", () => {
        const enabled = toggle.getAttribute("aria-pressed") !== "true";
        toggle.disabled = true;

        chrome.storage.local.set({ enabled }, () => {
            if (chrome.runtime.lastError) {
                console.error("NetScope could not save its enabled state:", chrome.runtime.lastError.message);
                showError("SAVE FAILED");
                toggle.disabled = false;
                return;
            }

            renderState(enabled);
        });
    });
})();
