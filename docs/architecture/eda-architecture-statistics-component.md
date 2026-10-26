# Statistics Component - Event-Driven Architecture

**Current implementation:** PostgreSQL outbox, RabbitMQ hosted by CloudAMQP, and the existing Spring Boot backend.

Saving a game or play updates the user's records immediately. A background process then calculates the statistics shown on the collection page.

## 1. The main building blocks

```mermaid
flowchart LR
    Save["Save a game or play change<br/>BoardGameService<br/>GamePlayService"]
    Outbox[("outbox_events<br/>Events waiting to be sent")]
    Sender["OutboxDispatcher<br/>and RabbitMqPublisher"]
    Queue["CloudAMQP RabbitMQ<br/>trackmyboardgames.statistics"]
    Receiver["StatisticsMessageConsumer<br/>and StatisticsRefreshService"]
    Result[("statistics_summaries<br/>Saved collection<br/>statistics")]

    Save -->|"Save the change and event together"| Outbox
    Outbox -->|"Pick up a pending event"| Sender
    Sender -->|"Send through the events exchange"| Queue
    Queue -->|"Deliver the message"| Receiver
    Receiver -->|"Recalculate and save"| Result

    classDef application fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef database fill:#f3e8ff,stroke:#9333ea,color:#581c87
    classDef messaging fill:#ffedd5,stroke:#ea580c,color:#7c2d12
    classDef result fill:#dbeafe,stroke:#2563eb,color:#172554

    class Save,Sender,Receiver application
    class Outbox database
    class Queue messaging
    class Result result
```

## Flow diagram

Follow the panels from top to bottom. Each panel starts a new row of the same story, keeping the class boxes readable instead of extending one very wide diagram. Repeated repository and RabbitMQ boxes are connection points between panels.

### A. Save the game and its event

```mermaid
classDiagram
    direction LR
    class Phone["📱 User"] {
        Adds or edits a game
        Presses Save
    }
    class Form["BoardGameForm.tsx"] {
        handleSubmit(event)
    }
    class Page["CollectionPage.tsx"] {
        handleSubmit(values, coverImage)
    }
    class Controller["BoardGameController.java"] {
        create(jwt, request)
        update(jwt, gameId, request)
    }
    class Service["BoardGameService.java"] {
        create(subjectIssuer, subject, request)
        update(subjectIssuer, subject, gameId, request)
        saveGame(game)
        saveGameEvent(eventType, ownerId, game, occurredAt)
    }
    class Games["BoardGameRepository.java"] {
        saveAndFlush(game)
    }
    class Outbox["OutboxEventRepository.java"] {
        save(event)
    }

    Phone --> Form : 1. Submit the form
    Form --> Page : 2. Call the parent's onSubmit callback
    Page --> Controller : 3. Call to Backend Java. HTTP POST for create or PUT for update
    Controller --> Service : 4. Call create(...) or update(...)
    Service --> Games : 5. saveGame calls saveAndFlush(game)
    Service --> Outbox : 6. saveGameEvent calls save(event)

    style Phone fill:#dbeafe,stroke:#2563eb,color:#172554
    style Form fill:#dbeafe,stroke:#2563eb,color:#172554
    style Page fill:#dbeafe,stroke:#2563eb,color:#172554
    style Controller fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Service fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Games fill:#f3e8ff,stroke:#9333ea,color:#581c87
    style Outbox fill:#f3e8ff,stroke:#9333ea,color:#581c87
```

Blue represents the user's browser; green represents backend code. Purple boxes are repository interfaces that access PostgreSQL. Their save methods are supplied by Spring Data JPA, so you will not find those method implementations written in the repository files. RabbitMQ is not called during these saves; the polling worker picks up the committed outbox event separately.

After the service transaction commits and changes are saved into the database tables, this HTTP response does **not** wait for RabbitMQ.

### B. Pick up the saved event and publish it

**Continues from A:** the event is now committed in `outbox_events` database table. A timer starts this next path.

```mermaid
classDiagram
    direction LR
    class Worker["OutboxPollingWorker.java"] {
        publishNext()
    }
    class Dispatcher["OutboxDispatcher.java"] {
        publishBatch(maxEvents)
    }
    class Claims["OutboxClaimService.java"] {
        claimNext(now)
        markPublished(eventId, claimToken, publishedAt)
        scheduleRetry(eventId, claimToken, nextAttemptAt, errorType)
    }
    class Outbox["OutboxEventRepository.java"] {
        findNextPublishable(now, pageable)
        findById(eventId)
    }
    class Publisher["RabbitMqPublisher.java"] {
        publish(event)
    }
    class Broker["RabbitMQ on CloudAMQP"] {
        Topic exchange: trackmyboardgames.events
        Statistics queue: trackmyboardgames.statistics
    }

    Worker --> Dispatcher : 7. Timer calls publishBatch(batchSize)
    Dispatcher --> Claims : 8. claimNext(now) reserves the next event
    Claims --> Outbox : Read and update one outbox row from A
    Dispatcher --> Publisher : 9. publish(event), one at a time
    Publisher --> Broker : 9a. Publish to the exchange with a routing key and wait for confirmation
    Dispatcher --> Claims : 10. markPublished(...) after confirmation

    style Worker fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Dispatcher fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Claims fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Outbox fill:#f3e8ff,stroke:#9333ea,color:#581c87
    style Publisher fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Broker fill:#ffedd5,stroke:#ea580c,color:#7c2d12
```

The default timer checks once a minute and attempts up to 10 events per check
(`RABBITMQ_BATCH_SIZE`), one at a time. Steps 8–10 repeat for each event, and the
worker claims each event immediately before sending it. A database lock is not
held while waiting for RabbitMQ. Confirmation means RabbitMQ accepted that
message, not that statistics finished updating. If sending fails,
`scheduleRetry(...)` leaves that event eligible for a later attempt instead of
marking it published; the batch can then continue with another due event.

Automatic publishing stops after **five total attempts**, including the initial
attempt. An unpublished row with `attempt_count >= 5` is treated as parked and
excluded from further claims. It stays in `outbox_events` with its last recorded
error for investigation; it is not sent to the consumer's dead-letter queue.
Attempts are counted when claimed, so a worker crash can also use an attempt.
There is no separate failed/parked column or automatic replay of exhausted events.

### C. Receive the message and save new statistics

**Continues from B:** RabbitMQ delivers the message independently. Delivery can happen before the sender finishes recording `published_at`; it does not wait for another polling check.

```mermaid
classDiagram
    direction LR
    class Broker["RabbitMQ on CloudAMQP"] {
        trackmyboardgames.statistics
    }
    class Consumer["StatisticsMessageConsumer.java"] {
        handleDelivery(delivery)
    }
    class Refresh["StatisticsRefreshService.java"] {
        refresh(eventId, ownerId)
        rebuildSummary(ownerId, now)
    }
    class Receipts["StatisticsProcessedEventRepository.java"] {
        recordIfNew(eventId, processedAt)
    }
    class Source["StatisticsSourceRepository.java"] {
        findGameTotals(ownerId)
        findMonthlyTotals(ownerId)
    }
    class Summaries["StatisticsSummaryRepository.java"] {
        saveAndFlush(summary)
    }

    Broker --> Consumer : 11. Deliver to consumer
    Consumer --> Refresh : 12. Validate message then call refresh(...)
    Refresh --> Receipts : 13. recordIfNew(...) skips repeated events
    Refresh --> Source : 14. Read current game and play totals
    Refresh --> Summaries : 15. rebuildSummary calls saveAndFlush(...)
    Consumer --> Broker : 16. Ack after commit

    class ReceiptRole["Have we handled this event already?"] {
        Store receipts in statistics_processed_events
        Do not read outbox_events
    }
    class SourceRole["What are the current game and play totals?"] {
        Read original game and play records
        Supply totals for the calculations
    }
    class SummaryRole["Save or retrieve the calculated answers"] {
        Store results in statistics_summaries
        Supply saved answers to the statistics API
    }
    Receipts ..> ReceiptRole : Responsibility
    Source ..> SourceRole : Responsibility
    Summaries ..> SummaryRole : Responsibility

    style Broker fill:#ffedd5,stroke:#ea580c,color:#7c2d12
    style Consumer fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Refresh fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Receipts fill:#f3e8ff,stroke:#9333ea,color:#581c87
    style Source fill:#f3e8ff,stroke:#9333ea,color:#581c87
    style Summaries fill:#f3e8ff,stroke:#9333ea,color:#581c87
    style ReceiptRole fill:#dbeafe,stroke:#2563eb,color:#172554
    style SourceRole fill:#dbeafe,stroke:#2563eb,color:#172554
    style SummaryRole fill:#dbeafe,stroke:#2563eb,color:#172554
```

The blue responsibility boxes explain the repositories; their dashed arrows are not additional processing steps.

`refresh(...)` first waits for any other calculation for the same user to finish,
using `AppUserRepository.findForStatisticsRefresh(...)` (omitted from the picture).
A repeated event skips the calculation and is acknowledged.

For a new event, the service writes a processing receipt to
`statistics_processed_events` and saves the owner's newly calculated results
(collection size, most-played game, most recently played game, and monthly play
counts) to `statistics_summaries`. Both database writes belong to one transaction.
Committing means making both writes permanent together; if the transaction fails,
neither write is kept. The receiver requeues or dead-letters failed messages.

### D. Read the saved statistics back into the page

**Continues from C through saved data, not a push notification:** the user refreshes the browser or leaves and returns to the collection page. There is no automatic statistics request after saving a game. If this read happens before C finishes, the page gets the previously saved results.

```mermaid
classDiagram
    direction LR
    class UI["CollectionStatistics.tsx"] {
        loadStatistics()
        setState(loadedStatistics)
    }
    class API["statisticsApi.ts"] {
        fetchStatistics(accessToken, signal)
    }
    class Controller["StatisticsController.java"] {
        get(jwt)
    }
    class Read["StatisticsReadService.java"] {
        read(issuer, subject)
    }
    class Summaries["StatisticsSummaryRepository.java"] {
        findById(ownerId)
    }

    UI --> API : 17. Fetch statistics
    API --> Controller : 18. HTTP GET /api/statistics
    Controller --> Read : 19. read(issuer, subject)
    Read --> Summaries : 20. Find saved results
    Summaries ..> Read : Saved summary
    Read ..> Controller : Return StatisticsResponse
    Controller ..> API : Return JSON over HTTP
    API ..> UI : 21. Update cards

    style UI fill:#dbeafe,stroke:#2563eb,color:#172554
    style API fill:#dbeafe,stroke:#2563eb,color:#172554
    style Controller fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Read fill:#dcfce7,stroke:#16a34a,color:#14532d
    style Summaries fill:#f3e8ff,stroke:#9333ea,color:#581c87
```

The dotted return arrows show data travelling back to React. `setState` is the React setter used inside the component, not a separate API method. The read service finds the signed-in user's account before looking up its summary; it does not calculate statistics. Game covers come from the collection's separate game-list request.

For play sessions, the corresponding page is `PlayHistoryPage.tsx` and the backend entry point is `GamePlayController`, under `/api/games/{gameId}/plays`. Its service saves play-change events into the same outbox; panels B through D then follow the same background delivery and statistics-read path.

### Building-block reference

| Piece | Plain-English purpose | Name in the code or database |
| --- | --- | --- |
| Event | A note saying a game or play changed | `GameAdded`, `GameUpdated`, `GameDeleted`, `PlayRecorded`, `PlayUpdated`, `PlayDeleted` |
| Outbox | Keeps the note until the sender can deliver it | `OutboxEvent`, stored in `outbox_events` |
| Sender | Picks up saved notes and sends them to RabbitMQ | `OutboxPollingWorker`, `OutboxDispatcher`, `RabbitMqPublisher` |
| Exchange and queue | Route messages and hold them for the receiver | `trackmyboardgames.events` and `trackmyboardgames.statistics` |
| Receiver | Checks a message and requests a statistics calculation | `StatisticsMessageConsumer` |
| Calculator | Recounts the user's current games and plays | `StatisticsRefreshService` |
| Saved results | Holds the collection count, selected games, and monthly play counts | `StatisticsSummary`, stored in `statistics_summaries` |
| Receipt book | Remembers events already handled so repeats are skipped | `StatisticsProcessedEvent`, stored in `statistics_processed_events` |

The sender watches the **outbox**, not the games or play-history tables. Saving a change deliberately saves its event in the same database transaction: either both succeed or neither does.

The receiver recalculates from **current database records**, rather than adding counts from an old message. This handles edits, deletes, and messages that arrive late. Calculations for the same user take turns; different users do not share that user-specific lock.

## 2. Delivery, retries, and failures

```mermaid
flowchart TD
    Save["Save game/play change and outbox event together"]
    Claim["Polling worker reserves one due event"]
    Send{"RabbitMQ confirms the send?"}
    Wait["Keep event pending<br/>Schedule a later retry"]
    Published["Mark the outbox event published"]
    Receive["RabbitMQ delivers to StatisticsMessageConsumer"]
    Valid{"Known event and valid message?"}
    Process["StatisticsRefreshService<br/>Skip a duplicate, or save receipt and new statistics together"]
    Success{"Database transaction succeeds?"}
    Retry{"Temporary database failure<br/>and not marked redelivered?"}
    Requeue["Requeue for an immediate retry"]
    Ack["Acknowledge the message<br/>RabbitMQ can remove it"]
    DLQ["Dead-letter route<br/>trackmyboardgames.statistics.dlq"]
    Inspect["Inspect the failure and fix its cause<br/>Replay manually if appropriate"]

    Save --> Claim --> Send
    Send -->|"No"| Wait
    Wait -->|"When due and picked up by polling"| Claim
    Send -->|"Yes"| Published
    Send -->|"Confirmed message can be delivered independently"| Receive
    Receive --> Valid
    Valid -->|"No"| DLQ
    Valid -->|"Yes"| Process --> Success
    Success -->|"Yes, including a skipped duplicate"| Ack
    Success -->|"No: receipt and statistics roll back"| Retry
    Retry -->|"Yes"| Requeue --> Receive
    Retry -->|"No"| DLQ --> Inspect

    classDef application fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef messaging fill:#ffedd5,stroke:#ea580c,color:#7c2d12
    classDef failure fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    class Save,Claim,Published,Process,Ack application
    class Receive,Requeue messaging
    class Wait,DLQ,Inspect failure
```

### If sending fails

- The event stays in the outbox. Retry delays start at five seconds and grow to a maximum of five minutes.
- A retry delay is the earliest it becomes eligible; the polling worker must still pick it up.
- A reserved event has a 30-second lease. If the backend stops before finishing, an expired reservation can be picked up later.
- If RabbitMQ accepted a message but saving "published" fails, the message may be sent again. This is why the receiver remembers processed event IDs.

### If receiving or calculating fails

- RabbitMQ delivers one unfinished message at a time to this receiver. It does not wait for the publisher's polling timer.
- Invalid messages go to the dead-letter queue through `trackmyboardgames.events.dlx`, using the route `statistics.failed`.
- A temporary database failure on a message not marked redelivered is requeued immediately. A processing failure on a redelivered message, or another kind of processing failure, goes to the dead-letter queue.
- "Redelivered" is not an exact attempt count: it can also follow a lost connection. The retry rule is deliberately conservative.
- The receiver acknowledges only after the database transaction commits. If the acknowledgement is lost, RabbitMQ may deliver again; the saved receipt prevents repeating the calculation.
- Dead-letter messages are **not automatically replayed**. Check the cause before manually replaying them. A broker-side consumer cancellation requires checking the queue and restarting the receiver.

## 3. How the classes work together

This is a class communication map, not a full list of every field and method. Arrows point from the caller to the class it uses. The orange RabbitMQ box is an external service, not a Java class; its outgoing arrow represents message delivery rather than a direct Java method call.

```mermaid
flowchart TB
    subgraph Changes["Saving changes"]
        Games["BoardGameService<br/>create / update / delete"]
        Plays["GamePlayService<br/>create / update / delete"]
        OutboxRepo["OutboxEventRepository<br/>Saves OutboxEvent records"]
        Games -->|"Save a game-change event"| OutboxRepo
        Plays -->|"Save a play-change event"| OutboxRepo
    end

    subgraph Sending["Sending saved events"]
        Worker["OutboxPollingWorker<br/>publishNext()"]
        Dispatcher["OutboxDispatcher<br/>publishBatch(maxEvents)"]
        Claims["OutboxClaimService<br/>claimNext() / markPublished()<br/>scheduleRetry()"]
        Publisher["RabbitMqPublisher<br/>publish(event)"]
        Worker -->|"Starts a bounded batch"| Dispatcher
        Dispatcher -->|"Claim each event; save success or retry"| Claims
        Claims -->|"Read and update saved events"| OutboxRepo
        Dispatcher -->|"Send events one at a time"| Publisher
    end

    Broker["RabbitMQ on CloudAMQP<br/>Topic exchange: trackmyboardgames.events<br/>Statistics queue: trackmyboardgames.statistics"]
    Publisher -->|"Publish to exchange with routing key and wait for confirmation"| Broker

    subgraph Calculating["Receiving and calculating"]
        Consumer["StatisticsMessageConsumer<br/>handleDelivery(delivery)"]
        Refresh["StatisticsRefreshService<br/>refresh(eventId, ownerId)<br/>backfill(ownerId)"]
        Backfill["StatisticsBackfillRunner<br/>Optional startup calculation"]
        Users["AppUserRepository<br/>Let calculations for one user take turns"]
        Receipts["StatisticsProcessedEventRepository<br/>Remember handled event IDs"]
        Source["StatisticsSourceRepository<br/>Read current game and play totals"]
        Summaries["StatisticsSummaryRepository<br/>Save and read StatisticsSummary"]
        Consumer -->|"Check message; request calculation"| Refresh
        Backfill -->|"Calculate existing collections"| Refresh
        Refresh -->|"Wait for this user's turn"| Users
        Refresh -->|"Skip repeats during event processing"| Receipts
        Refresh -->|"Load totals"| Source
        Refresh -->|"Save new results"| Summaries
    end

    Broker -->|"Deliver message"| Consumer
    Consumer -->|"Acknowledge, requeue, or reject"| Broker

    subgraph Reading["Displaying saved results"]
        UI["React CollectionStatistics<br/>fetchStatistics()"]
        Controller["StatisticsController<br/>GET /api/statistics"]
        Read["StatisticsReadService<br/>read(issuer, subject)"]
        Response["StatisticsResponse<br/>Counts and selected GameSummary objects"]
        UI -->|"Authenticated HTTP request"| Controller
        Controller -->|"Ask for saved statistics"| Read
        Read -->|"Find the signed-in user's account"| Users
        Read -->|"Read saved results only"| Summaries
        Read -->|"Build the response"| Response
    end

    classDef application fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef database fill:#f3e8ff,stroke:#9333ea,color:#581c87
    classDef messaging fill:#ffedd5,stroke:#ea580c,color:#7c2d12
    classDef result fill:#dbeafe,stroke:#2563eb,color:#172554
    class Games,Plays,Worker,Dispatcher,Claims,Publisher,Consumer,Refresh,Backfill,Controller,Read application
    class OutboxRepo,Users,Receipts,Source,Summaries database
    class Broker messaging
    class UI,Response result
```

**Colors:** green is application work, purple is database access, orange is messaging, and blue is the page or returned data. Repository boxes are Java classes/interfaces that access PostgreSQL, not separate services.

Reading statistics and calculating statistics are separate paths. Opening the page calls `StatisticsReadService`, not `StatisticsRefreshService`. The response travels back through the controller to React; those return arrows are omitted to keep the picture readable.

The backfill uses the same calculation and user lock, but does not record an event receipt. RabbitMQ connections and application setup classes are omitted; the retry and dead-letter decisions are shown in diagram 2.

## What the collection page reads

`CollectionStatistics` requests `GET /api/statistics` when the collection page opens. `StatisticsController` and `StatisticsReadService` return the signed-in user's saved answers; this request does not calculate statistics or send events.

The cards show:

- **Collection:** number of games.
- **Most Popular!:** the most-played game's title/edition and available cover.
- **Plays This Month!:** number of plays with a played-on date in the current UTC month.
- **Latest To The Table!:** the most recently played game's title/edition and available cover.

The two selected games are `GameSummary` objects. Covers come from the collection's already-loaded games, matched by game ID. Missing results are blank; a calculated count of zero displays as zero.

There is **no frontend timer, statistics refresh button, or live push update**. Refreshing the browser or leaving and returning reads the latest saved results.

## Starting data and current limits

- `StatisticsBackfillRunner` calculates starting results for existing collections without sending messages. Enable `STATISTICS_BACKFILL_ENABLED=true` for the intended development database, then disable it after successful startup. Leaving it enabled repeats the job at every restart.
- RabbitMQ publishing is enabled with `RABBITMQ_ENABLED`; receiving is enabled separately with `RABBITMQ_CONSUMER_ENABLED`. Keep credentials in private settings, not tracked documentation.
- The publisher attempts up to 10 events per polling check by default, sequentially
  (`RABBITMQ_BATCH_SIZE`). The default interval is 60 seconds
  (`RABBITMQ_POLL_INTERVAL_MS`).
- Publishing and receiving run inside the same Spring Boot application, with separate RabbitMQ connections. The backend can run locally while connecting to a development CloudAMQP broker over the internet.

See the [root README](../../README.md) for setup and test details. The [deprecated SQS building blocks](deprecated/SQS-EDA-BUILDING-BLOCKS.md) preserve the earlier proposal; they do not describe the running solution.
