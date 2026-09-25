# HTTP Request With Thread (Android)

An interview demo Android application that places a grocery order through the Training Data API using Java worker threads and direct Java `Socket`/`SSLSocket` networking—without Retrofit, OkHttp, Volley, or another HTTP client library.

## What it demonstrates

This project shows that an Android app can use Java's networking primitives directly. A raw TCP socket connects to the API host on port 443, is layered into an `SSLSocket`, and performs TLS with SNI and HTTPS endpoint verification. A small in-project HTTP/1.1 codec writes requests and parses responses. The blocking work is started on named Java threads rather than the UI thread.

This is deliberately a narrow educational implementation, **not** a general-purpose or production replacement for a maintained HTTP client. Real applications should normally use a supported networking library for connection pooling, protocol edge cases, cancellation, retries, and broader platform integration.

## Request and UI flow

1. `OrderScreen` observes state from `OrderViewModel` and renders the catalog, product selection, order form, quote, and result.
2. On startup, the ViewModel starts a `load-catalog-and-riders` Java thread. It fetches the catalog, starts independent product-image fetch/decode threads, then fetches available riders on the original worker thread.
3. The user chooses a product and quantity and requests a quote. The app posts to the quote endpoint and stores the returned quote.
4. After the customer form is valid, the user confirms the order. The app posts the quote ID and customer details to the order endpoint.
5. The ViewModel publishes loading, progress, success, and error state for Compose to display. An expired quote requires a new quote. If the transport fails after an order request may have reached the server, the UI marks the outcome unknown and prevents a blind retry that could duplicate an order.

The initial catalog and rider calls are sequential on one worker thread. Product-image requests may run concurrently on their own threads. Quote and order calls are separate user-triggered actions and must occur in that order because the order requires the quote ID.

## Project structure

- `app/src/main/java/.../ui/OrderScreen.kt` — Compose order screen.
- `app/src/main/java/.../ui/OrderViewModel.kt` and `OrderUiState.kt` — background task orchestration and observable UI state.
- `app/src/main/java/.../data/repository/GroceryRepository.kt` — repository operations and workflow-facing API.
- `app/src/main/java/.../data/TrainingDataApi.kt` — endpoint paths, request/response mapping, and API-level errors.
- `app/src/main/java/.../data/network/RawHttpsClient.kt` — direct socket-to-TLS transport.
- `app/src/main/java/.../data/network/Http1Codec.kt` — minimal HTTP/1.1 request/response codec.
- `app/src/main/java/.../data/json/MiniJson.kt` — small JSON parser/writer used by the demo.
- `app/src/main/java/.../data/image/` — product image fetching/decoding support.
- `app/src/test/` — JVM unit tests for the HTTP codec, JSON, request/image mapping, form validation, and ViewModel behavior.
- `app/src/androidTest/` — Android instrumentation test source.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `gradle/wrapper/` — Android build configuration and Gradle wrapper.

## Requirements and build

- Android Studio with Android SDK Platform 37 installed.
- JDK 21 for the Gradle daemon (the repository's Gradle daemon JVM configuration requests it).
- An Android emulator or device running API 33 or newer to install and interact with the app.
- Network access to `https://train.uxi.asia` for live API requests and product images.

Run JVM unit tests without launching the app:

```bash
./gradlew :app:testDebugUnitTest
```

Build the debug APK without installing or launching it:

```bash
./gradlew :app:assembleDebug
```

To try the UI, open the project in Android Studio, select an API 33+ emulator/device, and run the `app` configuration. Startup loads the public catalog/riders; creating a quote and confirming an order invokes live mutation endpoints.

## HTTP/1.1 codec scope

`Http1Codec` writes a single HTTP/1.1 request per connection, asks the server to close the connection, and requests identity content encoding. It supports response bodies framed by:

- `Content-Length`;
- `Transfer-Encoding: chunked`, including chunk extensions and bounded trailer headers;
- connection close when neither framing header is present.

It also handles up to four interim (1xx) responses other than protocol switching and correctly omits bodies for `HEAD`, 204, and 304 responses. Defensive limits are 16 KiB per line, 200 response headers (also applied to trailers), and 2 MiB per response body. Connect and read timeouts are configured by the transport.

This small codec does not aim to support every HTTP feature or adversarial server behavior. For example, it doesn't provide keep-alive pooling, compression, redirects, proxy configuration, HTTP/2, a general cookie system, or a mature cancellation/retry policy. The implementation is included to make the mechanics visible for learning and discussion.

## API endpoints and side effects

The app talks to `https://train.uxi.asia` using:

- `GET /rest/v1/grocery_catalog` — retrieve active grocery products.
- `GET /assets/grocery-images/:filename` — fetch product images when present in the catalog.
- `GET /rest/v1/delivery_riders` — retrieve available riders.
- `POST /rest/v1/rpc/create_order_quote` — request a quote for the selected product and quantity.
- `POST /rest/v1/rpc/create_delivery_order` — confirm a quote with customer and delivery details.

Catalog, image, and rider reads are public. Quote and order operations require an API key in the `x-api-key` header. For the interview walkthrough, a demo key is temporarily embedded in the app source, so it will be visible in this public repository. It is temporary/demo-only and must not be used for production; deactivate or rotate it after the panel has finished trying the app. Its value is intentionally not repeated in this README.

**Placing an order creates a real record and reduces stock in the live PostgreSQL database.** A successful quote by itself does not place an order, but the order button does. Avoid test orders unless you intend that side effect.

## Tests

The local JVM test suite covers the custom HTTP response framing and validation, JSON parsing/serialization, image/API mapping, order form validation, and ViewModel sequencing and error states. `ExampleInstrumentedTest` is the Android instrumentation-test entry point; running it requires a configured emulator/device and is separate from the JVM tests above.
