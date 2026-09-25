# HTTP Request With Thread

Android Compose demo that performs HTTPS requests using Java worker threads, `Socket`/`SSLSocket`, and a small custom HTTP/1.1 codec instead of Retrofit, OkHttp, or Volley.

The complete Android project is in [`HttpRequestWithThread/`](HttpRequestWithThread/), with the detailed interview guide in [`HttpRequestWithThread/README.md`](HttpRequestWithThread/README.md). It covers the UI/repository flow, codec framing support and limits, tests, build commands, and why this is an educational client rather than a general-purpose HTTP implementation.

To build and test from a clone of this repository:

```bash
cd HttpRequestWithThread
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The quote/order flow uses the live API; confirming an order creates a real record and deducts stock. The temporary demo API key is embedded in the source and visible in this public repository; deactivate or rotate it after the interview demo.
