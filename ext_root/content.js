(() => {
    const descriptionClass = "netscope-result-description";
    // Keep the badge and URL list limited to the first ten linked results.
    const maxResults = 10;

    function addResultDescriptions() {
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

        links.forEach((link) => {
            // The observer can revisit existing results, so skip links already updated.
            if (link.dataset.netscopeDescriptionAdded) {
                return;
            }

            const description = document.createElement("div");
            description.className = descriptionClass;
            description.textContent = "Description placeholder";
            // Table layout keeps the green badge sized to its text instead of the full row.
            description.style.cssText = "display: table; max-width: 100%; padding: 6px 10px; margin: 4px 0; background-color: #00c853; color: #102a16; font: bold 14px/1.4 Arial, sans-serif; border-radius: 4px;";
            // Find the site's label in the closest result card, including when it sits outside the link.
            const result = link.closest(".MjjYud, .g") || link.parentElement;
            const siteName = link.querySelector(".VuuXrf") || result.querySelector(".VuuXrf");
            const siteNameRow = siteName && siteName.parentElement;
            if (siteNameRow) {
                // Place the badge after the site label and before the result title.
                siteNameRow.insertAdjacentElement("afterend", description);
            } else {
                // Fall back to the title when this result has no recognizable site label.
                const heading = link.querySelector("h3");
                (heading || link).insertAdjacentElement("beforebegin", description);
            }
            link.dataset.netscopeDescriptionAdded = "true";
        });
    }

    addResultDescriptions();
    // Google can append or replace result cards after document_idle.
    new MutationObserver(addResultDescriptions).observe(document.documentElement, {
        childList: true,
        subtree: true
    });
})();
