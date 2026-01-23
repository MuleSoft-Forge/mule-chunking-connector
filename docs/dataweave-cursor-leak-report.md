# DataWeave Cursor Leak in foreach - Streaming Bug Report

## Summary
DataWeave's `ArrayType.accepts()` type-checking logic opens cursors on `CursorIteratorProvider` instances but never releases them, causing memory leaks and blocking sliding-window eviction strategies.

## Environment
- **Mule Runtime**: 4.10.2
- **DataWeave**: 2.10.2
- **Component**: `foreach` with repeatable streaming

## Reproduction

### Test Flow
```xml
<chunking:read-chunked chunkSize="#[10 * 1024 * 1024]">
    <chunking:streaming-strategy>
        <chunking:sliding-window maxCachedChunks="10" />
    </chunking:streaming-strategy>
</chunking:read-chunked>
<scatter-gather>
    <route>
        <foreach>
            <!-- Process chunks -->
        </foreach>
    </route>
    <route>
        <foreach>
            <!-- Process chunks -->
        </foreach>
    </route>
</scatter-gather>
```

### Observed Behavior
1. Each `foreach` opens **2 cursors** on the `CursorIteratorProvider`
2. **First cursor** (type-checking): Opened by `ArrayType.accepts()`, remains at position 0, **never released**
3. **Second cursor** (iteration): Opened by `splitInArraySeq()`, advances normally, released at end

### Evidence from Stack Traces

**Type-checking cursor** (never advances, never released):
```
at org.mule.weave.core@2.10.2/org.mule.weave.v2.model.types.ArrayType$.accepts(Type.scala:667)
at org.mule.weave.mule.service.weave/org.mule.weave.v2.el.WeaveExpressionLanguageSession.splitInArraySeq(WeaveExpressionLanguageSession.scala:192)
```

**Iteration cursor** (normal behavior):
```
at org.mule.weave.mule.service.weave/org.mule.weave.v2.el.WeaveExpressionLanguageSession.splitInArraySeq(WeaveExpressionLanguageSession.scala:192)
at org.mule.weave.mule.service.weave/org.mule.weave.v2.el.WeaveExpressionLanguageSession.split(WeaveExpressionLanguageSession.scala:348)
```

### Detailed Cursor Activity

**Route 0 (foreach #1):**
- Cursor #2129521722: Created by `ArrayType.accepts()`, **never calls next()**, never released
- Cursor #763465407: Normal iteration, processes chunks 0→10

**Route 1 (foreach #2):**
- Cursor #1354773259: Created by `ArrayType.accepts()`, **never calls next()**, never released
- Cursor #2082812968: Normal iteration, processes chunks 0→10

## Impact

### Memory Leaks
- Unreleased cursors hold references to streaming infrastructure
- Prevents garbage collection of cursor metadata
- Accumulates over long-running flows

### Sliding-Window Eviction Blocked
When implementing bounded-memory streaming with sliding windows:
1. Eviction strategy calculates `minPosition = min(all cursor positions)`
2. Type-checking cursors stuck at position 0 block eviction: `minPosition = 0`
3. No chunks can be evicted (all chunks ≥ 0)
4. Cache grows unbounded despite `maxCachedChunks` limit
5. Results in `StreamingBufferSizeExceededException` or OOM

### Error Log
```
ERROR Sliding-window cache exceeded maximum size.
Cache size: 10, maxCachedChunks: 10, active cursors: 4.
Cursor positions: [0, 0, 10, 10]
```

## Root Cause

`ArrayType.accepts()` in DataWeave core opens a cursor to validate that a `CursorIteratorProvider` is actually iterable, but:
1. **Does not release the cursor** after type checking
2. Leaves cursor at position 0 (never calls `next()`)
3. Cursor remains in provider's `activeCursors` set indefinitely

This appears to be in:
- `org.mule.weave.core@2.10.2/org.mule.weave.v2.model.types.ArrayType$.accepts(Type.scala:667)`

## Expected Behavior

Cursors opened for type validation should be:
1. Released immediately after validation completes
2. Wrapped in try-finally to ensure cleanup
3. Excluded from iteration semantics (not tracked by provider)

## Workaround

For custom streaming connectors, detect and ignore DataWeave type-checking cursors:
```java
// Check if cursor was opened by DataWeave type checking
StackTraceElement[] stack = Thread.currentThread().getStackTrace();
boolean isDataWeaveTypeCheck = Arrays.stream(stack)
    .anyMatch(ste ->
        "org.mule.weave.v2.model.types.ArrayType$".equals(ste.getClassName()) &&
        "accepts".equals(ste.getMethodName()));
```

## Questions for MuleSoft Team

1. Is this a known issue in Mule 4.10.2 / DataWeave 2.10.2?
2. Is there a fix planned or available in newer versions?
3. Should `ArrayType.accepts()` use a try-with-resources pattern for cursors?
4. Should type-checking cursors be marked differently (e.g., not added to `activeCursors`)?

## Additional Context

This was discovered while implementing a chunking connector with sliding-window strategy for bounded-memory processing of large files. The connector works correctly with single `foreach` (2 cursors: 1 zombie, 1 active), but fails with `scatter-gather` containing multiple `foreach` (4 cursors: 2 zombies, 2 active) because zombie cursors block eviction.

Full debug logs available on request.
