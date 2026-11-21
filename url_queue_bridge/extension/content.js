// Turns Google's redirect links into their destination URLs.
function getDestinationUrl(href) {
    const url = new URL(href);
    if (url.pathname === "/url") {
        return url.searchParams.get("q") || url.searchParams.get("url") || "";
    }
    return url.href;
}

// Reads up to ten valid links from the search results, in page order.
const urls = Array.from(document.querySelectorAll("#search h3, #rso h3"))
    .map((heading) => heading.closest("a"))
    .filter(Boolean)
    .map((link) => getDestinationUrl(link.href))
    .filter((url) => /^https?:\/\//i.test(url))
    .slice(0, 10);

// Sends the extracted URLs to the extension's Java-server connector.
if (urls.length > 0) {
    chrome.runtime.sendMessage({ type: "queueUrls", urls }, (response) => {
        if (chrome.runtime.lastError) {
            console.error("Could not send URLs:", chrome.runtime.lastError.message);
        } else if (!response || !response.ok) {
            console.error("Could not save URLs:", response && response.error);
        }
    });
}
