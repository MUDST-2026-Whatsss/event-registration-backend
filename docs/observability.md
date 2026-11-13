# Observability (Grafana stack)

The API can be observed through the three signal types, all viewed in Grafana:

| Signal | Produced by | Collected / stored by | What you see |
|---|---|---|---|
| Metrics | Spring Boot Actuator + Micrometer at `/actuator/prometheus` | Prometheus scrapes every 15 s | Request rate, errors, latency, JVM, CPU, DB pool |
| Logs | The API's console log (and every other container in the project) | Grafana Alloy reads Docker logs and pushes to Loki | Searchable logs labelled by `service` and `level` |
| Traces | Micrometer Tracing + OpenTelemetry, exported over OTLP | Tempo | One trace per HTTP request (security filter spans included) and per scheduled task; database queries are not traced as separate spans |

```text
                 ┌─ /actuator/prometheus ──(pull)── Prometheus ─┐
 Spring Boot API ┼─ stdout ── Docker ── Alloy ──(push)── Loki ───┼── Grafana :3000
                 └─ OTLP http :4318 ──────(push)──── Tempo ──────┘
```

The signals are linked: a log line carries `[traceId-spanId]`, so Grafana turns the trace id into a
link to Tempo; a trace offers "Logs for this span"; latency graphs show exemplar dots that open the
trace of a slow request.

> Promtail, shown in many tutorials, reached end of life; Grafana Alloy is its replacement and does
> the same job here.

## Run it

Layer the observability file on the stack you already use:

```bash
# Managed PostgreSQL (docker-compose.server.yml)
docker compose -f docker-compose.server.yml -f docker-compose.observability.yml up -d

# Local PostgreSQL built from source
docker compose -f docker-compose.yml -f docker-compose.build.yml -f docker-compose.observability.yml up -d --build
```

| URL | What |
|---|---|
| http://localhost:3000 | Grafana, user `admin`, password `GRAFANA_ADMIN_PASSWORD` (default `admin`) |
| http://localhost:9090 | Prometheus (targets under Status → Targets, rules under Alerts) |
| http://localhost:12345 | Alloy pipeline UI |

Grafana opens on the **Event Registration – Observability** dashboard. Use the site for a minute
(browse events, sign in, register) so there is traffic to see.

Stop only the observability services with:

```bash
docker compose -f docker-compose.server.yml -f docker-compose.observability.yml \
  stop prometheus loki tempo alloy grafana
```

Running the API without the overlay keeps the previous behaviour: only `/actuator/health` is
exposed and no traces are exported.

## Check that it works

1. **Metrics**: Prometheus → Status → Target health shows `event-registration-api` as **UP**.
2. **Traffic**: open http://localhost:5173 and click around for a minute; the Overview row of the
   dashboard shows requests per second and latency.
3. **Logs**: the "API logs" panel fills in; set `Log level` to `WARN` or `ERROR` to filter.
4. **Log → trace**: most requests write no log line, so produce one. This burst trips the per-IP
   rate limit for about a minute (only for your machine's IP on port 8080; the website is not
   affected):

   ```bash
   seq 1 600 | xargs -P 30 -I{} curl -s -o /dev/null localhost:8080/api/v1/events
   ```

   A `WARN ... Rate limit exceeded` line appears. Expand it and click **View trace** next to
   `TraceID` to open the request in Tempo.
5. **Trace → logs**: in the trace view, click **Logs for this span**.
6. **Exemplars**: on the Latency panel, hover a dot and click the trace link.
7. **Alerts**: Prometheus → Alerts lists five rules as *inactive*. To see `ApiDown` fire, stop the
   API (`docker compose ... stop api`) and wait about a minute.

## Dashboard

- **Overview**: API up/down, requests per second, 5xx error rate, p95 latency.
- **HTTP**: requests by endpoint and status, p50/p95/p99 latency with exemplars, slowest endpoints.
- **JVM and database**: heap, CPU, HikariCP active/idle/pending connections.
- **Logs**: log volume by level and the API log stream, filtered by the `Log level` and
  `Log search` variables at the top.
- **Traces**: the latest API traces; click one to open the span timeline.
- **Alerts**: alerts that are currently firing.

Explore (compass icon) is the place for ad-hoc queries, for example:

```text
# PromQL: requests per second for one endpoint
sum(rate(http_server_requests_seconds_count{uri="/api/v1/events"}[1m]))

# LogQL: API errors
{service="api", level="ERROR"}

# TraceQL: requests slower than 500 ms
{resource.service.name="event-registration" && duration > 500ms}
```

## Alerts (baselines)

`observability/prometheus/alerts.yml` defines the baselines Prometheus evaluates every 15 s:

| Alert | Fires when |
|---|---|
| `ApiDown` | the API misses scrapes for 1 minute |
| `HighServerErrorRate` | more than 5% of requests return 5xx for 5 minutes |
| `HighLatencyP95` | p95 latency is above 1 s for 5 minutes |
| `HighHeapUsage` | heap usage is above 90% for 5 minutes |
| `DatabaseConnectionsPending` | requests wait for a DB connection for 2 minutes |

Alerts are shown in Prometheus and on the dashboard; no Alertmanager or notification channel is
configured.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MANAGEMENT_ENDPOINTS` | `health` (`health,prometheus` with the overlay) | Actuator endpoints exposed over HTTP |
| `TRACING_EXPORT_ENABLED` | `false` (`true` with the overlay) | Send traces over OTLP |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Share of requests traced |
| `OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` (`http://tempo:4318/v1/traces` with the overlay) | Trace receiver |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Grafana admin password |
| `GRAFANA_PORT` / `PROMETHEUS_PORT` / `ALLOY_PORT` | `3000` / `9090` / `12345` | Host ports, bound to 127.0.0.1 |

Retention: metrics 7 days, logs 7 days, traces 2 days, each in its own named volume.

`/actuator/prometheus` needs no sign-in so Prometheus can scrape it. It only exists when
`MANAGEMENT_ENDPOINTS` includes `prometheus`, and the frontend nginx does not proxy `/actuator`, so
it is not reachable through the website. It is reachable on the API's own published port
(`http://localhost:8080/actuator/prometheus`). Alloy mounts the Docker socket read-only to discover
containers; run this stack on a development machine, not a shared server.

## Files

```text
docker-compose.observability.yml
observability/
  prometheus/prometheus.yml   scrape targets
  prometheus/alerts.yml       alert rules
  loki/loki.yml               log storage
  tempo/tempo.yml             trace receiver and storage, span metrics
  alloy/config.alloy          Docker log discovery and shipping
  grafana/provisioning/       data sources and dashboard provider
  grafana/dashboards/         dashboard JSON
```
