(() => {
    const descriptionClass = "netscope-result-description";
    // Keep the badge and URL list limited to the first ten linked results.
    const maxResults = 10;
    let extensionEnabled = false;
    let analysisRequest = 0;
    const assessments = [];

    function saveAssessment(assessment) {
        const index = assessments.findIndex((item) => item.url === assessment.url);
        if (index === -1) {
            assessments.push(assessment);
        } else {
            assessments[index] = assessment;
        }
    }

    function unwrapGoogleUrl(href) {
        try {
            const url = new URL(href, window.location.origin);
            if (url.pathname === "/url") {
                return url.searchParams.get("q") || url.searchParams.get("url") || href;
            }
            return url.href;
        } catch (_error) {
            return href;
        }
    }

    function renderDescription(link) {
        const assessment = assessments.find((item) => item.url === link.dataset.netscopeUrl);
        if (!assessment) {
            return;
        }

        const description = document.createElement("div");
        description.className = descriptionClass;
        description.textContent = `${assessment.label} (${assessment.score}/100)`;
        description.title = assessment.flags && assessment.flags.length
            ? assessment.flags.join("; ")
            : "No URL warning signals found. This is not a reputation or malware check.";
        description.style.cssText =
            `display: table; max-width: 100%; padding: 6px 10px; margin: 4px 0; ` +
            `background-color: ${assessment.color}; color: #fff; font: bold 14px/1.4 Arial, sans-serif; ` +
            `border-radius: 4px;`;

        const result = link.closest(".MjjYud, .g") || link.parentElement;
        const siteName = link.querySelector(".VuuXrf") || (result && result.querySelector(".VuuXrf"));
        const siteNameRow = siteName && siteName.parentElement;
        if (siteNameRow) {
            siteNameRow.insertAdjacentElement("afterend", description);
        } else {
            const heading = link.querySelector("h3");
            (heading || link).insertAdjacentElement("beforebegin", description);
        }
    }

    function analyzeLinks(links) {
        const entries = links.map((link) => ({
            link,
            url: unwrapGoogleUrl(link.href)
        })).filter((entry) => /^https?:\/\//i.test(entry.url));
        const urls = entries.map((entry) => entry.url);
        entries.forEach((entry) => {
            entry.link.dataset.netscopeUrl = entry.url;
        });

        if (urls.length === 0) {
            return;
        }

        const requestId = ++analysisRequest;
        chrome.runtime.sendMessage({ type: "analyzeUrls", urls }, (response) => {
            if (chrome.runtime.lastError || !response || !response.ok) {
                entries.forEach((entry) => {
                    saveAssessment({
                        url: entry.url,
                        score: 0,
                        label: "Analyzer offline",
                        color: "#627087",
                        flags: [response && response.error ? response.error : "Could not connect to analyzer"]
                    });
                });
            } else {
                response.results.forEach((assessment) => {
                    saveAssessment(assessment);
                });
            }

            if (requestId !== analysisRequest) {
                return;
            }
            entries.forEach((entry) => renderDescription(entry.link));
        });
    }

    function addResultDescriptions() {
        if (!extensionEnabled) {
            return;
        }

        // Ignore Google pages that aren't showing a query's search results.
        if (window.location.pathname !== "/search" || !new URLSearchParams(window.location.search).has("q")) {
            return;
        }

        // Google uses both containers; discard headings without links before applying the limit.
        const links = Array.from(document.querySelectorAll("#search h3, #rso h3"))
            .map((heading) => heading.closest("a"))
            .filter(Boolean)
            .slice(0, maxResults);

        // Expose URLs to other extension scripts; some hrefs may be Google redirects.
        window.netscopeSearchResultUrls = links.map((link) => link.href);

        const pendingLinks = links.filter((link) => !link.dataset.netscopeDescriptionAdded);
        if (pendingLinks.length === 0) {
            return;
        }
        pendingLinks.forEach((link) => {
            link.dataset.netscopeDescriptionAdded = "true";
        });
        analyzeLinks(pendingLinks);
    }

    function setExtensionEnabled(enabled) {
        extensionEnabled = enabled;
        if (enabled) {
            addResultDescriptions();
            return;
        }

        document.querySelectorAll(`.${descriptionClass}`).forEach((description) => description.remove());
        document.querySelectorAll("[data-netscope-description-added]").forEach((link) => {
            delete link.dataset.netscopeDescriptionAdded;
        });
        window.netscopeSearchResultUrls = [];
    }

    chrome.storage.onChanged.addListener((changes, areaName) => {
        if (areaName === "local" && changes.enabled) {
            setExtensionEnabled(changes.enabled.newValue !== false);
        }
    });

    chrome.storage.local.get({ enabled: true }, (settings) => {
        if (chrome.runtime.lastError) {
            console.error("NetScope could not load its enabled state:", chrome.runtime.lastError.message);
            return;
        }

        setExtensionEnabled(settings.enabled !== false);
    });

    // Google can append or replace result cards after document_idle.
    new MutationObserver(addResultDescriptions).observe(document.documentElement, {
        childList: true,
        subtree: true
    });
})();
