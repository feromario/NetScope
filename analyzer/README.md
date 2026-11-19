# NetScope analyzer core

This dependency-free Java module demonstrates a priority queue, point-based
risk scoring, and a directed evidence graph. It does not fetch pages or check
external reputation databases. Its URL checks are heuristics, not a guarantee
that a site is safe. A score of zero means none of the URL warning patterns
matched, not that the destination was verified.

## Run with the Chrome extension

From this directory, run `start-analyzer.bat`. The local server listens only on
`127.0.0.1:8765`. Load `ext_root` as an unpacked Chrome extension and search on
Google. Keep the analyzer running while the extension is enabled.

The queue accepts HTTP/HTTPS links only, deduplicates normalized URLs, and
limits each search batch to its first ten results. Jobs are dequeued by
descending initial priority, then by Google result position. With no existing
reputation signal, callers can use `enqueueTopResults` to preserve search order.
The queue uses a priority queue for processing order and simple lists for
deduplication and graph storage.

Risk areas have provisional point caps and labels use provisional thresholds:
0-19 Low Risk, 20-49 Some Risk, and 50-100 Risky. Tune these against a labeled
backtest set before relying on them. The category colors are presentation
metadata; the extension remains responsible for rendering them accessibly.
The local server flags patterns such as insecure HTTP, IP-based or encoded
domains, URL shorteners, unusual ports, suspicious terms, long or encoded URLs,
large query strings, and URL parameters that appear to point to an external
redirect. It also checks a local known-domain list. Add one hostname per line
to `known-malicious-domains.txt` from a threat-intelligence source you trust;
the file is empty by default and is not updated automatically. No submitted
URLs are sent to an external service. These signals can produce false
positives and miss threats; they are intended for an educational demonstration.

The graph links each URL to its concerns, each concern to its evidence source
and score contribution, and each assessment to its resulting risk level.
Redirect edges can be recorded separately with `recordRedirect`.

To run the dependency-free test harness from PowerShell in this directory:

```powershell
$classes = Join-Path $env:TEMP "netscope-analyzer-classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = Get-ChildItem -Recurse -Filter *.java | ForEach-Object FullName
javac -Xlint:all -d $classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -ea -cp $classes com.netscope.analysis.NetScopeCoreTest
```
