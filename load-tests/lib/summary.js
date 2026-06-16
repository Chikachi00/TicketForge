export function summaryOutputs(data, scenarioName) {
  const safeName = scenarioName || (__ENV.SCENARIO_NAME || 'k6-summary');
  const path = `results/${safeName}-summary.json`;
  return {
    stdout: textSummary(data, safeName),
    [path]: JSON.stringify(data, null, 2)
  };
}

function textSummary(data, scenarioName) {
  const metrics = data.metrics || {};
  const duration = metric(metrics.http_req_duration);
  const requests = metric(metrics.http_reqs);
  const dropped = metric(metrics.dropped_iterations);
  return [
    `TicketForge k6 summary: ${scenarioName}`,
    `requests=${requests?.count ?? 0}`,
    `rps=${requests?.rate ?? 0}`,
    `p50=${duration?.['p(50)'] ?? 'n/a'}ms`,
    `p90=${duration?.['p(90)'] ?? 'n/a'}ms`,
    `p95=${duration?.['p(95)'] ?? 'n/a'}ms`,
    `p99=${duration?.['p(99)'] ?? 'n/a'}ms`,
    `max=${duration?.max ?? 'n/a'}ms`,
    `dropped_iterations=${dropped?.count ?? 0}`,
    ''
  ].join('\n');
}

function metric(value) {
  return value?.values;
}
