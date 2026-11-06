# RAS API – Logging and Auditing Enhancements

## 1. Overview of change

The existing RAS API logging and auditing capabilities have been enhanced to improve visibility, traceability and troubleshooting across downstream service interactions, particularly calls to the Data Exchange Service (DES).

Previously, investigating a failed request could involve searching through application logs and correlating information across multiple services. Some important identifiers were embedded in free-text log messages, making them harder to search and filter.

The changes introduce more consistent logging and structured audit events, with key identifiers such as **User ID** and **Correlation ID** available to support end-to-end request tracing.

### Key improvements

- Improved structured logging of important request and response information.
- Propagation of Correlation ID and User ID to downstream services where required.
- Enhanced DES audit events capturing request and response outcomes.
- Consistent recording of failure categories and relevant error details.
- Unit tests to verify logging, identifier propagation and audit events.
- Improved ability to investigate requests using Kibana and Splunk.

### High-level architecture

```text
       RAS Frontend
            |
            | Request + tracing identifiers
            v
         RAS API
            |
            | Structured application logs
            | Correlation ID + User ID
            v
       DesConnector
            |
            | HTTP POST + DES headers
            v
           DES
            |
            | HTTP response / exception
            v
       DesConnector
            |
       +----+----+
       |         |
    Logging   Auditing
       |         |
       v         v
     Kibana    Audit Service
                   |
                   v
                 Splunk
```

The objective is to make it easier to follow a request from the RAS API through to DES, understand its outcome and identify where a failure occurred.

---

## 2. API Logging specification

The logging enhancements aim to make application logs more consistent, searchable and useful for operational support.

### Key information captured

| Information | Purpose |
|---|---|
| User ID | Identifies the user associated with the request. |
| Correlation ID | Links related log entries across the request lifecycle and downstream services. |
| Operation / method | Identifies the API operation being performed. |
| Log level | Distinguishes informational messages, warnings and errors. |
| Error details | Helps identify the reason for a failure. |

### Correlation ID and User ID propagation

The Correlation ID provides a common reference for following a request across services.

The User ID helps identify which user's request is being processed.

```text
RAS Frontend
     |
     | Correlation ID: ABC-123
     | User ID: 12345678
     v
RAS API
     |
     | Same identifiers propagated
     v
DES
     |
     | Logs / audit events
     v
Investigation using Correlation ID
```

Using consistent identifiers means that related activity can be found without relying solely on timestamps or searching through free-text messages.

---

## 3. API Auditing specification

A new audit event has been introduced for DES residency-status requests:

**Audit type:** `ReliefAtSourceDesResidencyStatus`

The purpose is to record the outcome of each DES request attempt, including successful responses, unsuccessful responses and exceptions.

### Audit information captured

| Field | Description |
|---|---|
| `downstreamService` | Identifies the downstream service (`DES`). |
| `endpoint` | Records the endpoint being called. |
| `httpMethod` | Records the HTTP method (`POST`). |
| `userIdentifier` | Identifies the user associated with the request. |
| `nino` | Records the relevant National Insurance number. |
| `correlationId` | Links the audit event to the wider request journey. |
| `outcome` | Records `SUCCESS` or `FAILURE`. |
| `httpStatus` | Records the HTTP response status, where available. |
| `failureCategory` | Categorises the reason for failure. |
| `responseCode` | Records the relevant application or DES response code. |
| `errorDetail` | Provides additional failure information. |
| `processingPath` | Identifies the code path that generated the audit event. |
| `requestHeader_*` | Captures selected outgoing request headers for troubleshooting. |


### Failure categories

The implementation identifies different failure scenarios, including:

- `BAD_REQUEST`
- `MATCHING_FAILED`
- `FORBIDDEN`
- `TOO_MANY_REQUESTS`
- `REQUEST_TIMEOUT`
- `SERVICE_UNAVAILABLE`
- `UPSTREAM_ERROR`
- `EXCEPTION`
- `DECEASED`
- `MISSING_NEXT_YEAR_STATUS`
- `DES_FAILURE_RESPONSE`
- `UNEXPECTED_RESPONSE`

This allows failures to be categorised and investigated more easily than relying on HTTP status codes alone.

### Success and failure audit flow

```text
              DES request
                  |
                  v
             DES response
                  |
                  v
          resolveResponse()
                  |
          +-------+-------+
          |               |
       Success           Failure
          |               |
          v               v
     Audit SUCCESS    Audit FAILURE
                          |
                          v
                  Failure category
                  + error details


       HTTP exception / timeout
                  |
                  v
          recover() block
                  |
                  v
             Audit FAILURE
```


---

## 4. New data structures created

The audit implementation uses structured key-value data rather than a single free-text message.

### Audit data

The audit information is represented as:

```scala
Map[String, String]
```

This provides named fields that can be independently searched and filtered.

For example:

```scala
Map(
  "downstreamService" -> "DES",
  "endpoint" -> uri,
  "httpMethod" -> "POST",
  "userIdentifier" -> userId,
  "correlationId" -> corrId,
  "outcome" -> "FAILURE",
  "failureCategory" -> "FORBIDDEN",
  "httpStatus" -> "403"
)
```

Optional fields are included only when relevant or available. For example, an exception or timeout may not have an HTTP response status.

### Header audit data

Selected outgoing request headers are transformed into structured audit fields:

```scala
Map(
  "requestHeader_Environment" -> "Staging",
  "requestHeader_OriginatorId" -> "DA_RAS",
  "requestHeader_CorrelationId" -> corrId
)
```

The authorisation header is excluded to prevent sensitive credentials like the bearer token from being recorded.

---

## 5. New methods / functions created

### `auditDesResidencyStatusRequest`

A shared helper method responsible for constructing and submitting the DES audit event.

It accepts the request and response context, including:

- User ID and NINO.
- Correlation ID.
- Outgoing DES headers.
- Endpoint.
- HTTP status and outcome.
- Failure category and response details.
- Processing path.

It builds the structured audit map and submits it through `AuditService`.

### `resolveResponse`

Handles HTTP responses from DES.

It determines whether the response represents a successful business outcome or a failure, then invokes the audit helper with the appropriate details.

### `recover`

Handles exceptions and unsuccessful HTTP calls.

The recovery logic identifies the type of failure, records the available HTTP status and error information, and invokes the audit helper.

### Audit execution points

```text
sendResidencyStatusRequest()
          |
          v
       DES call
          |
          v
    HTTP response
          |
          v
   resolveResponse()
          |
          v
 auditDesResidencyStatusRequest()


   HTTP exception
          |
          v
       recover()
          |
          v
 auditDesResidencyStatusRequest()
```

Auditing takes place for each downstream attempt, including retries, so that the individual DES calls and their outcomes can be investigated.

---

## 6. New way to test logging using uk.gov.hmrc.play.bootstrap.tools.LogCapturing

A new approach using withCaptureOfLoggingFrom was introduced to capture log events during unit tests.

This allows tests to check the actual log level and message produced by the application, rather than just checking that a method was called.

The approach was used in the DES Connector tests to verify three scenarios:

* ERROR: Confirms that a failed DES call logs an error containing the user ID and correlation ID.
* WARN: Confirms that a warning is logged when a retryable DES call is retried, including the retry count and correlation ID.
* DEBUG: Confirms that debug information is logged when DES returns a failure response body.

The captured log events are passed to logMessagesWithLevel, which extracts the log level and message so they can be checked against the expected values.

### Example assertion

```scala
logMessagesWithLevel(logs) shouldBe List(
  Level.ERROR -> s"[DesConnector][getResidencyStatus] Cannot provide a residency status for this pension scheme member for userId ($userId) correlationId ($expectedCorrId)."
)
```

This verifies both:

- The expected log level (`ERROR`).
- The expected log message, including the relevant identifiers.

---

## 7. Unit tests added

Unit tests have been added to verify the new logging, propagation and auditing behaviour.

### Logging and propagation tests

- Verify that relevant log messages contain the expected User ID and Correlation ID.
- Verify the correct log level is used.
- Verify Correlation ID and User ID are propagated to downstream requests.
- Verify the expected error message is logged when DES returns an unsuccessful response.

### Auditing tests

- Successful DES response produces a `SUCCESS` audit event.
- Forbidden response produces a `FAILURE` event with the `FORBIDDEN` category.
- Parsed DES failure responses include the relevant response code and error details.
- Missing mandatory next-year residency status produces the appropriate failure audit event.
- Relevant outgoing request headers are included in the audit data.
- The authorisation bearer token is not included in the audit data.

These tests help ensure that the audit information remains consistent as the connector evolves.

---

## 8. Ways to search for logging in Kibana

The structured logging improvements make it easier to search for related application activity.

The following examples illustrate how to investigate requests using common identifiers.

### Search by Correlation ID

Find log entries associated with a particular request:

```text
correlationId: "ABC-123"
```

### Search by User ID

Find activity associated with a particular user:

```text
userId: "12345678"
```

### Search by both identifiers

Narrow the search to a particular user's request:

```text
userId: "12345678"
AND correlationId: "ABC-123"
```

### Search for errors

Find error-level messages associated with the DES connector:

```text
level: "ERROR"
AND correlationId: "ABC-123"
```

### Search for DES-related activity

```text
DesConnector
AND correlationId: "ABC-123"
```

**Note:** These are illustrative Kibana queries. The exact field names and syntax depend on the configured log format and index mappings. If an identifier is still embedded in a free-text message rather than indexed as a structured field, search the message text instead.

---

## 9. How failures and DES processing can be traced in Splunk

The new audit event provides structured information that can be used to investigate DES requests and their outcomes.

### Example: Investigating a failed DES request

Suppose a user reports that their residency-status request failed.

The investigation can follow these steps:

**Step 1 – Identify the request**

Search using the Correlation ID or User ID.

**Step 2 – Find the DES audit event**

Filter for the new audit type:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
```

**Step 3 – Identify unsuccessful requests**

```spl
auditType="ReliefAtSourceDesResidencyStatus"
outcome="FAILURE"
```

**Step 4 – Identify the failure category**

For example, investigate forbidden responses:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
failureCategory="FORBIDDEN"
```

**Step 5 – Review the details**

Inspect:

- `httpStatus`
- `failureCategory`
- `responseCode`
- `errorDetail`
- `correlationId`
- `processingPath`
- `requestHeader_Environment`
- `requestHeader_OriginatorId`

This helps establish what was requested, which downstream service was involved, where the failure was handled and what information was available at the time.

### Example: Tracing a request across services

```text
User request
     |
     v
RAS Frontend
     |
     v
RAS API
     |
     | Correlation ID: ABC-123
     v
DES request
     |
     | Same Correlation ID
     v
DES response
     |
     +------------------+
     |                  |
  Success             Failure
     |                  |
     v                  v
SUCCESS audit       FAILURE audit
     |                  |
     +--------+---------+
              |
              v
       Audit Service
              |
              v
            Splunk
              |
              v
       Search by Correlation ID
```

### Example Splunk searches

Find all DES audit events:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
```

Find failed DES requests:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
outcome="FAILURE"
```

Find a particular user's failed requests:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
userIdentifier="12345678"
outcome="FAILURE"
```

Find requests associated with a particular Correlation ID:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
correlationId="ABC-123"
```

Find timeout failures:

```spl
auditType="ReliefAtSourceDesResidencyStatus"
failureCategory="REQUEST_TIMEOUT"
```

### Investigating file-processing failures

Where a DES interaction forms part of a wider file-processing journey, the same investigation approach can be extended using the identifiers available in the relevant application logs and audit events.

For example:

1. Identify the affected file using its available file identifier or other processing metadata.
2. Find the associated RAS API logs and audit events.
3. Follow the Correlation ID to locate related downstream requests.
4. Inspect the recorded HTTP status, failure category and error details.
5. Compare the timestamps and processing paths to establish where the failure occurred.

This helps distinguish between failures occurring within RAS API, during the downstream DES call, or while processing the response.

**Important:** The residency-status audit event described here does not itself record a complete DES file-processing history. End-to-end file tracing depends on the relevant file identifiers and audit/logging coverage being available in the other stages of that journey.

---

## 10. Benefits

The logging and auditing enhancements provide several practical benefits:

| Area | Benefit |
|---|---|
| Troubleshooting | Faster identification of where and why a request failed. |
| Traceability | Correlation ID links related activity across services. |
| User-level investigation | User ID helps locate requests associated with a particular user. |
| Monitoring | Failure categories make it easier to identify recurring problems. |
| Auditing | Structured records provide a consistent history of downstream request outcomes. |
| Support | Developers and operational teams can investigate issues using Kibana and Splunk. |
| Testing | Unit tests verify that logging, propagation and audit behaviour work as expected. |

### Summary

These changes improve the observability of the RAS API by making important request identifiers, downstream interactions and failure information easier to capture, search and investigate.

Together, structured logging, identifier propagation and enhanced auditing provide a clearer picture of the request lifecycle and help reduce the time needed to diagnose issues involving DES and other downstream services.