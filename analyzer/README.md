# NetScope analyzer core

This dependency-free Java module provides the URL assessment queue, point-based
risk scoring, and directed evidence graph. It does not fetch or open URLs; a
trusted signal collector must supply `RiskConcern` evidence to `RiskScorer`.

The queue accepts HTTP/HTTPS links only, deduplicates normalized URLs, and
limits each search batch to its first ten results. Jobs are dequeued by
descending initial priority, then by Google result position. With no existing
reputation signal, callers can use `enqueueTopResults` to preserve search order.

Risk areas have provisional point caps and labels use provisional thresholds:
0-19 Low Risk, 20-49 Some Risk, and 50-100 Risky. Tune these against a labeled
backtest set before relying on them. The category colors are presentation
metadata; the extension remains responsible for rendering them accessibly.

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
