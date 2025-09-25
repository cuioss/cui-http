# TODO: Modernize ExponentialBackoffRetryStrategy with Virtual Threads

## Problem Statement

The current `ExponentialBackoffRetryStrategy` uses `Thread.sleep()` for delays between retry attempts, which blocks the executing thread. This is inefficient in high-concurrency scenarios and can lead to thread starvation in traditional thread pool environments.

**Current Implementation Issues:**
1. **Thread Blocking**: `Thread.sleep()` blocks the entire thread, wasting resources
2. **Poor Scalability**: In thread pool environments, blocking threads reduces throughput
3. **Limited Flexibility**: Synchronous API prevents efficient async operation composition
4. **Resource Waste**: Blocked threads consume memory but do no useful work during delays

## Proposed Solution

Leverage Java 21's Virtual Threads to create a modern, efficient retry mechanism that doesn't suffer from traditional thread blocking issues.

### Architecture Overview

```java
public interface RetryStrategy {
    // Single async API using Virtual Threads (no sync version)
    <T> CompletableFuture<HttpResultObject<T>> execute(
        HttpOperation<T> operation,
        RetryContext context
    );
}
```

### Implementation Strategy

#### Option 1: CompletableFuture.delayedExecutor() (Recommended)

```java
public <T> CompletableFuture<HttpResultObject<T>> execute(
    HttpOperation<T> operation,
    RetryContext context
) {
    return executeAttempt(operation, context, 1);
}

private <T> CompletableFuture<HttpResultObject<T>> executeAttempt(
    HttpOperation<T> operation,
    RetryContext context,
    int attempt
) {
    // Execute on virtual thread
    return CompletableFuture
        .supplyAsync(operation::execute, Executors.newVirtualThreadPerTaskExecutor())
        .thenCompose(result -> {
            if (result.isValid() || !result.isRetryable() || attempt >= maxAttempts) {
                return CompletableFuture.completedFuture(result);
            }

            // Calculate delay and retry with CompletableFuture.delayedExecutor
            Duration delay = calculateDelay(attempt);
            Executor delayedExecutor = CompletableFuture.delayedExecutor(
                delay.toMillis(), TimeUnit.MILLISECONDS,
                Executors.newVirtualThreadPerTaskExecutor()
            );

            return CompletableFuture
                .supplyAsync(() -> executeAttempt(operation, context, attempt + 1), delayedExecutor)
                .thenCompose(future -> future);
        });
}
```

#### Option 2: Hybrid ScheduledExecutorService + Virtual Threads

```java
public class HybridRetryStrategy {
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(); // Small scheduler for timing
    private final Executor virtualExecutor =
        Executors.newVirtualThreadPerTaskExecutor(); // Virtual threads for work

    public <T> CompletableFuture<HttpResultObject<T>> execute(
        HttpOperation<T> operation,
        RetryContext context
    ) {
        return executeAttempt(operation, context, 1);
    }

    private <T> CompletableFuture<HttpResultObject<T>> executeAttempt(
        HttpOperation<T> operation,
        RetryContext context,
        int attempt
    ) {
        return CompletableFuture
            .supplyAsync(operation::execute, virtualExecutor)
            .thenCompose(result -> {
                if (result.isValid() || !result.isRetryable() || attempt >= maxAttempts) {
                    return CompletableFuture.completedFuture(result);
                }

                Duration delay = calculateDelay(attempt);
                CompletableFuture<HttpResultObject<T>> future = new CompletableFuture<>();

                scheduler.schedule(() -> {
                    executeAttempt(operation, context, attempt + 1)
                        .whenComplete((res, ex) -> {
                            if (ex != null) future.completeExceptionally(ex);
                            else future.complete(res);
                        });
                }, delay.toMillis(), TimeUnit.MILLISECONDS);

                return future;
            });
    }
}
```

## Analysis of Industry Solutions

### Resilience4j Approach
- Uses `ScheduledExecutorService` for delays ✅
- `Retry.decorateCompletionStage()` requires ScheduledExecutorService parameter
- **Issue**: Not optimized for virtual threads (predates Java 21)
- **Solution**: Use hybrid approach with virtual thread executors

### Spring Retry Evolution
- **Old**: Uses `Thread.sleep()` in synchronous mode ❌
- **New**: Spring Boot 3.2+ adds `SimpleAsyncTaskScheduler` with virtual thread support ✅
- Auto-configures virtual threads when `spring.threads.virtual.enabled=true`
- **Lesson**: Even Spring moved away from Thread.sleep()

### Project Reactor (Mono/Flux)
- Uses reactor's internal scheduler for delays ✅
- `Mono.delay()` and `retryWhen()` operators - proper non-blocking delays
- **Lesson**: Reactive frameworks never use Thread.sleep() for delays

### Key Industry Insight
All modern async libraries avoid Thread.sleep() and use proper delay mechanisms (schedulers, timers, etc.)

## Solution

**Research Finding**: Thread.sleep() is NOT the best approach, even with virtual threads. Better alternatives exist:

1. **CompletableFuture.delayedExecutor()** - Proper non-blocking delay mechanism (Java 9+)
2. **Hybrid ScheduledExecutorService + Virtual Threads** - Optimal resource utilization
3. **No Thread.sleep()** - Avoids blocking and scheduler limitations

**Recommended Approach**: CompletableFuture.delayedExecutor() with virtual thread executor because:

1. **Non-blocking**: Doesn't park threads during delays
2. **Composable**: Integrates naturally with CompletableFuture chains
3. **Resource Efficient**: Uses proper JVM delay mechanisms
4. **Scalable**: Handles thousands of concurrent delayed operations
5. **Modern**: Designed for async programming patterns

## Implementation Plan

**Pre-1.0 Rule**: This library is pre-1.0, so we make breaking changes without deprecation or transition periods.

### Phase 1: Replace Sync with Async API
1. Change `RetryStrategy` interface to return `CompletableFuture<HttpResultObject<T>>`
2. Update all implementations to use virtual threads
3. Remove synchronous `execute()` method entirely

### Phase 2: Update All Consumers
1. Update `ResilientHttpHandler` to handle async retry results
2. Update all callers to use CompletableFuture API
3. Update tests to handle async operations

## Testing Strategy

1. **Unit Tests**: Verify retry logic with deterministic delays
2. **Concurrency Tests**: Validate behavior under high concurrent load
3. **Performance Tests**: Measure throughput improvements with virtual threads
4. **Integration Tests**: Verify async operation composition works correctly

## Code Changes Required

```java
// Before (synchronous)
HttpResultObject<String> result = retryStrategy.execute(
    () -> httpClient.send(request),
    context
);

// After (only async API exists)
HttpResultObject<String> result = retryStrategy.execute(
    () -> httpClient.send(request),
    context
).get(); // Block if synchronous behavior needed

// After (fully async with composition)
retryStrategy.execute(() -> httpClient.send(request), context)
    .thenCompose(result -> processResult(result))
    .thenAccept(processed -> updateCache(processed))
    .exceptionally(ex -> handleError(ex));
```

## Benefits

### CompletableFuture.delayedExecutor() Advantages:
1. **True Non-blocking**: No thread parking during delays
2. **Composable**: Natural integration with async pipelines
3. **JVM Optimized**: Uses internal JVM timer mechanisms
4. **Scalable**: Handles millions of concurrent delayed operations
5. **Resource Efficient**: Minimal memory overhead for delays
6. **Modern Design**: Built for async programming patterns

### Why Not Thread.sleep() (Even with Virtual Threads):
1. **Still blocks**: Parks virtual thread during delay
2. **No composition**: Doesn't integrate with CompletableFuture chains
3. **Scheduler limitations**: Virtual thread scheduler doesn't time-share
4. **Resource waste**: Consumes carrier thread during sleep
5. **Not designed for delays**: Thread.sleep() simulates blocking, not scheduling

## Open Questions

1. ~~Should we provide both sync and async APIs or migrate fully to async?~~ **Decision: Async only (pre-1.0 rule)**
2. ~~Should we use Thread.sleep() with virtual threads?~~ **Decision: No, use CompletableFuture.delayedExecutor()**
3. How to handle retry metrics with async operations?
4. Should we add reactive streams (Flow API) support?
5. Integration with existing observability tools?
6. Should we create a single ScheduledExecutorService for all retry delays or per-strategy?

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [CompletableFuture.delayedExecutor() JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html#delayedExecutor(long,java.util.concurrent.TimeUnit))
- [Virtual Threads with ScheduledExecutorService - Baeldung](https://www.baeldung.com/java-scheduledexecutorservice-virtual-threads)
- [Spring Boot Virtual Threads](https://spring.io/blog/2022/10/11/embracing-virtual-threads)
- [Virtual Thread Performance Analysis - Alibaba](https://www.alibabacloud.com/blog/exploration-of-java-virtual-threads-and-performance-analysis_601860)
- [Async Retry Pattern Examples](https://nurkiewicz.com/2013/07/asynchronous-retry-pattern.html)