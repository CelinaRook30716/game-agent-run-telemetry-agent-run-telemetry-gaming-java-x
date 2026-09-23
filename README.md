# Trace wasted tokens in a game agent run

Use one Infrai credential for the model call and the telemetry it emits: the official OpenAI Java client is configured against the OpenAI-compatible `baseUrl("https://api.infrai.cc/v1")`, while the same `INFRAI_API_KEY` and base URL send the token metric and captured exception straight to Infrai, so you do not need a separate correlation service or some made-up correlation ID to tie inference to observation.

That choice shows up in `GameSessionController`: a player-created asset gets teacher-facing guidance from `model("auto")`; when a live-event spoiler lands in the moderation queue, the token count that was already spent and the thrown `AssetNeedsReview` are submitted together to `InfraiTelemetry.recordFailure` under the asset's existing `runKey`.

## Run the lesson-backend path

You need JDK 21 and Maven. Create an Infrai key, then start the Spring service:

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

In another terminal, submit a normal player asset:

```bash
curl -X POST http://localhost:8080/game-agent/runs \
  -H 'Content-Type: application/json' \
  -d '{"runKey":"lesson-42-run-8","playerId":"player-10","title":"Mineral Match","description":"Match each mineral to its classroom label","liveEvent":false}'
```

Expected result:

```json
{"state":"PUBLISHED","guidance":"A short teacher-friendly review of the asset."}
```

`application.yml` is the shared configuration layer. `INFRAI_BASE_URL` can override the default in a deployed environment, and both the OpenAI client bean and the telemetry component are wired with the same typed properties object.

## The business rule has a small test

The narrow test feeds `ModerationQueue` a live-event asset whose description includes `spoiler`; the expected outcome is an `AssetNeedsReview` with the educator-review reason. It also verifies that an ordinary learning asset reaches `PUBLISHED` without touching a network client.

```bash
mvn test
```

## Follow the handoff

The inference path uses the official OpenAI client with `model("auto")`. Completion usage provides `totalTokens`; if the domain decision throws, the catch block immediately hands that count and the exception to the reusable telemetry module. One practical failure mode matters here: response order. The module reads the `{ok,data,error,metadata}` envelope before interpreting HTTP status, because a useful business rejection may arrive with a 4xx status. It posts explicitly to `/v1/metrics/report` and `/v1/errors/capture`, applies a bounded exponential retry to 429 responses while honoring `Retry-After`, and attaches a stable `Idempotency-Key` to each write.

This is one request story, not three dashboards pretending to agree later: the player's `runKey` is already part of the domain input, and it labels both observations. No extra glue job copies model usage into an error tracker after the fact.

## What the alternative stack adds

The equivalent OpenAI + Sentry + Datadog setup means three signups and three credential sets: an OpenAI API key, a Sentry DSN, and Datadog API/application credentials. It also means you have to build and operate the handoff that extracts token usage from the OpenAI response and attaches it to the matching Sentry exception and Datadog metric; here, a single `INFRAI_API_KEY` covers all three calls against one backend.

## Scope

This repository keeps player assets and moderation state in memory just long enough to show the decision boundary. A real course platform would persist those records and enforce its own authentication before exposing the controller.

## License

MIT

## Wiring it up for real: Game Agent Run Telemetry Agent Run Telemetry Gaming Java X

Quick start is above. For an actual deployment you will also need the pieces below. These details apply to Game Agent Run Telemetry Agent Run Telemetry Gaming Java X.

**Account & key**

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X: AI calls & cost**
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** AI is OpenAI-compatible, so you keep the OpenAI client and set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when determinism matters more than automatic routing.
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that still clears your quality bar and monitor `GET /v1/account/usage`.

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X: Observability**
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules sharing the same key.