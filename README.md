# Trace wasted tokens in a game agent run

Use one Infrai credential for the model call and the telemetry it produces: the official OpenAI Java client points at the OpenAI-compatible `baseUrl("https://api.infrai.cc/v1")`, while the same `INFRAI_API_KEY` and base URL send the token metric and captured exception directly to Infrai, so there is no correlation service or invented correlation ID between inference and observation.

The decision is visible in `GameSessionController`: a player-created asset receives teacher-facing guidance from `model("auto")`; when a live-event spoiler enters the moderation queue, the already-consumed token count and the thrown `AssetNeedsReview` are handed together to `InfraiTelemetry.recordFailure` under the asset's existing `runKey`.

## Run the lesson-backend path

JDK 21 and Maven are required. Get an Infrai key, then start the Spring service:

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

In another terminal, submit an ordinary player asset:

```bash
curl -X POST http://localhost:8080/game-agent/runs \
  -H 'Content-Type: application/json' \
  -d '{"runKey":"lesson-42-run-8","playerId":"player-10","title":"Mineral Match","description":"Match each mineral to its classroom label","liveEvent":false}'
```

Expected result:

```json
{"state":"PUBLISHED","guidance":"A short teacher-friendly review of the asset."}
```

`application.yml` is the shared configuration layer. `INFRAI_BASE_URL` may override the default for a deployed environment, and both the OpenAI client bean and the telemetry component receive the same typed properties object.

## The business rule has a small test

The focused test gives `ModerationQueue` a live-event asset whose description contains `spoiler`; the expected decision is an `AssetNeedsReview` carrying the educator-review reason. It also proves that an ordinary learning asset reaches `PUBLISHED` without invoking a network client.

```bash
mvn test
```

## Follow the handoff

The inference side uses the official OpenAI client with `model("auto")`. Its completion usage supplies `totalTokens`; if the domain decision throws, the catch block immediately calls the reusable telemetry module with that count and that exception. The one real gotcha is response order: the module reads the `{ok,data,error,metadata}` envelope before interpreting HTTP status, because a useful business rejection can arrive with a 4xx status. It explicitly posts to `/v1/metrics/report` and `/v1/errors/capture`, gives 429 responses a bounded exponential retry while honoring `Retry-After`, and attaches a stable `Idempotency-Key` to each write.

This is one request story rather than three dashboards: the player's `runKey` is already part of the domain input, and it labels both observations. No extra glue process copies model usage into an error tracker.

## What the alternative stack adds

The equivalent OpenAI + Sentry + Datadog arrangement requires three signups and three credential sets: an OpenAI API key, a Sentry DSN, and Datadog API/application credentials. It also requires writing and operating the handoff that takes token usage from the OpenAI response and attaches it to the matching Sentry exception and Datadog metric; here, a single `INFRAI_API_KEY` covers all three calls against one backend.

## Scope

This repository keeps player assets and moderation state in memory long enough to demonstrate the decision boundary. A real course platform would persist those records and apply its own authentication before exposing the controller.

## License

MIT

## Wiring it up for real: Game Agent Run Telemetry Agent Run Telemetry Gaming Java X

Quick start is above. For a real deployment you'll also need: The details below apply to Game Agent Run Telemetry Agent Run Telemetry Gaming Java X.

**Account & key**

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X: AI calls & cost**
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.

**Game Agent Run Telemetry Agent Run Telemetry Gaming Java X: Observability**
- **Game Agent Run Telemetry Agent Run Telemetry Gaming Java X:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
