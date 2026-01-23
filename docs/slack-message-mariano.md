# Slack Message to Mariano Achaval

Hey Mariano! 👋

I've discovered a cursor leak in DataWeave that's affecting repeatable streaming strategies. Wanted to get your thoughts on this.

## TL;DR
DataWeave's `ArrayType.accepts()` opens cursors for type validation in `foreach` but never releases them, creating "zombie cursors" that remain at position 0 indefinitely.

## The Bug

**Location:** `org.mule.weave.v2.model.types.ArrayType$.accepts(Type.scala:667)`
**Affected:** Mule 4.10.2 / DataWeave 2.10.2

When `foreach` receives a `CursorIteratorProvider`:
1. DataWeave calls `ArrayType.accepts()` to validate it's iterable
2. This opens a cursor but **never releases it**
3. A second cursor is opened for actual iteration (this one works fine)
4. The type-checking cursor remains at position 0 forever

## Evidence

In a scatter-gather with 2 foreach loops, I see **4 cursors** created:
```
Route 0:
  - Cursor #2129521722: ArrayType.accepts() - never calls next(), never released ❌
  - Cursor #763465407: Normal iteration - works correctly ✅

Route 1:
  - Cursor #1354773259: ArrayType.accepts() - never calls next(), never released ❌
  - Cursor #2082812968: Normal iteration - works correctly ✅
```

Stack trace for zombie cursors:
```
at org.mule.weave.v2.model.types.ArrayType$.accepts(Type.scala:667)
at org.mule.weave.v2.el.WeaveExpressionLanguageSession.splitInArraySeq(...)
at org.mule.runtime.core.internal.routing.Foreach.splitRequest(...)
```

## Impact

For **sliding-window eviction strategies**, this is a showstopper:
- Eviction calculates `minPosition = min(all cursor positions)`
- Zombie cursors stuck at 0 → `minPosition = 0`
- No chunks can be evicted (all chunks ≥ 0)
- Cache grows unbounded → OOM

Example error:
```
Cache size: 10, maxCachedChunks: 10, active cursors: 4
Cursor positions: [0, 0, 10, 10]
```

## Temporary Workaround

I've implemented stack-trace-based detection in my connector:
```java
private boolean isDataWeaveTypeCheckingCursor() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    for (StackTraceElement ste : stack) {
        if (ste.getClassName().contains("ArrayType") &&
            ste.getMethodName().equals("accepts")) {
            // Exclude this cursor from eviction calculation
            return true;
        }
    }
    return false;
}
```

But this feels like a hack. 😅

## Questions

1. Is this a known issue in 4.10.2?
2. Is there a fix in newer Mule versions?
3. Should `ArrayType.accepts()` use try-with-resources for cursors?
4. Or should type-checking cursors be marked differently (not added to `activeCursors`)?

The proper fix would be in DataWeave's type validation logic to either:
- Use try-with-resources: `try (CursorIterator c = provider.openCursor()) { ... }`
- Or use a different validation mechanism that doesn't open cursors

I have full debug logs if you want to dig into this. Let me know your thoughts!

Full write-up: [attached as separate doc]
