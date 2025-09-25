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

#### Option 1: Virtual Thread with Sleep (Simplest)

```java
public <T> CompletableFuture<HttpResultObject<T>> executeAsync(
    HttpOperation<T> operation,
    RetryContext context
) {
    return CompletableFuture.supplyAsync(() -> {
        // Virtual threads make Thread.sleep() acceptable
        // The JVM will park the virtual thread without blocking OS threads
        return executeSynchronous(operation, context);
    }, Executors.newVirtualThreadPerTaskExecutor());
}

private <T> HttpResultObject<T> executeSynchronous(
    HttpOperation<T> operation,
    RetryContext context
) {
    // Existing logic with Thread.sleep() - acceptable with virtual threads
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        HttpResultObject<T> result = operation.execute();
        if (result.isValid() || !result.isRetryable()) {
            return result;
        }

        if (attempt < maxAttempts) {
            Duration delay = calculateDelay(attempt);
            Thread.sleep(delay.toMillis()); // OK with virtual threads!
        }
    }
    return lastResult;
}
```

#### Option 2: Custom Virtual Thread Scheduler (More Complex)

```java
public class VirtualThreadRetryScheduler {

    public <T> CompletableFuture<HttpResultObject<T>> scheduleRetry(
        HttpOperation<T> operation,
        RetryContext context,
        int attempt
    ) {
        if (attempt > maxAttempts) {
            return CompletableFuture.completedFuture(lastResult);
        }

        // Execute operation on virtual thread
        return CompletableFuture
            .supplyAsync(operation::execute, Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(result -> {
                if (result.isValid() || !result.isRetryable() || attempt == maxAttempts) {
                    return CompletableFuture.completedFuture(result);
                }

                // Schedule next attempt after delay
                Duration delay = calculateDelay(attempt);
                return delayedExecutor(delay)
                    .thenCompose(__ -> scheduleRetry(operation, context, attempt + 1));
            });
    }

    private CompletableFuture<Void> delayedExecutor(Duration delay) {
        return CompletableFuture.runAsync(
            () -> {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            },
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
```

#### Option 3: Hybrid Approach with Fallback

```java
public class ModernRetryStrategy {
    private static final boolean VIRTUAL_THREADS_AVAILABLE = checkVirtualThreadSupport();
    private final Executor executor;

    public ModernRetryStrategy() {
        this.executor = VIRTUAL_THREADS_AVAILABLE
            ? Executors.newVirtualThreadPerTaskExecutor()
            : ForkJoinPool.commonPool(); // Fallback for pre-21 JVMs
    }

    public <T> CompletableFuture<HttpResultObject<T>> executeAsync(
        HttpOperation<T> operation,
        RetryContext context
    ) {
        if (VIRTUAL_THREADS_AVAILABLE) {
            // Use simple Thread.sleep with virtual threads
            return CompletableFuture.supplyAsync(
                () -> executeSynchronous(operation, context),
                executor
            );
        } else {
            // Use ScheduledExecutorService for platform threads
            return executeWithScheduler(operation, context);
        }
    }
}
```

## Analysis of Industry Solutions

### Resilience4j Approach
- Uses `ScheduledExecutorService` for delays
- Requires explicit executor for async operations
- `Retry.decorateCompletionStage()` requires ScheduledExecutorService parameter
- Not optimized for virtual threads (predates Java 21)

### Spring Retry
- Uses `Thread.sleep()` in synchronous mode
- `@Retryable` annotation with AOP
- Spring Boot 3.2+ adds `SimpleAsyncTaskScheduler` with virtual thread support
- Auto-configures virtual threads when `spring.threads.virtual.enabled=true`

### Project Reactor (Mono/Flux)
- Uses reactor's internal scheduler for delays
- `Mono.delay()` and `retryWhen()` operators
- Non-blocking by design
- Virtual thread support via `Schedulers.boundedElastic()`

## Recommended Solution

Given Java 21 as minimum version, recommend **Option 1** (Virtual Thread with Sleep) because:

1. **Simplicity**: Minimal code changes, reuses existing synchronous logic
2. **Efficiency**: Virtual threads handle blocking operations efficiently
3. **Works with existing `HttpOperation` interface**: No need to change operation signatures
4. **Performance**: Virtual threads automatically yield during `Thread.sleep()`
5. **Maintainability**: Less complex than custom schedulers

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

### Phase 3: Optimization
1. Add metrics for virtual thread usage
2. Implement adaptive retry delays based on system load
3. Add circuit breaker integration

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

1. **Non-blocking**: Virtual threads don't block OS threads during delays
2. **Scalable**: Can handle thousands of concurrent retry operations
3. **Resource Efficient**: Virtual threads have minimal memory overhead
4. **Simple**: Retains straightforward programming model
5. **Future-proof**: Aligns with Java platform direction

## Open Questions

1. ~~Should we provide both sync and async APIs or migrate fully to async?~~ **Decision: Async only (pre-1.0 rule)**
2. How to handle retry metrics with async operations?
3. Should we add reactive streams (Flow API) support?
4. Integration with existing observability tools?

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Resilience4j Retry Documentation](https://resilience4j.readme.io/docs/retry)
- [Spring Boot Virtual Threads](https://spring.io/blog/2022/10/11/embracing-virtual-threads)
- [Java 21 Virtual Threads Guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)