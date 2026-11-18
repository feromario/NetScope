(() => {
    const descriptionClass = "netscope-result-description";
    const maxResults = 10;

    function addResultDescriptions() {
        if (window.location.pathname !== "/search" || !new URLSearchParams(window.location.search).has("q")) {
            return;
        }

        const links = Array.from(document.querySelectorAll("#search h3, #rso h3"))
            .map((heading) => heading.closest("a"))
            .filter(Boolean)
            .slice(0, maxResults);

        links.forEach((link) => {
            if (link.dataset.netscopeDescriptionAdded) {
                return;
            }

            const description = document.createElement("div");
            description.className = descriptionClass;
            description.textContent = "Description placeholder";
            description.style.cssText = "display: table; max-width: 100%; padding: 6px 10px; margin: 4px 0; background-color: #00c853; color: #102a16; font: bold 14px/1.4 Arial, sans-serif; border-radius: 4px;";
            const result = link.closest(".MjjYud, .g") || link.parentElement;
            const siteName = link.querySelector(".VuuXrf") || result.querySelector(".VuuXrf");
            const siteNameRow = siteName && siteName.parentElement;
            if (siteNameRow) {
                siteNameRow.insertAdjacentElement("afterend", description);
            } else {
                const heading = link.querySelector("h3");
                (heading || link).insertAdjacentElement("beforebegin", description);
            }
            link.dataset.netscopeDescriptionAdded = "true";
        });
    }

    addResultDescriptions();
    new MutationObserver(addResultDescriptions).observe(document.documentElement, {
        childList: true,
        subtree: true
    });
})();
