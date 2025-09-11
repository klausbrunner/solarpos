package net.e175.klaus.solarpos;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

record PerformanceTracker(long startTime, AtomicLong itemCount) {

  PerformanceTracker() {
    this(System.nanoTime(), new AtomicLong());
  }

  static PerformanceTracker create(boolean enabled) {
    return enabled ? new PerformanceTracker() : null;
  }

  <T> Stream<T> trackStream(Stream<T> stream) {
    return stream.peek(item -> itemCount.incrementAndGet());
  }

  void printStats() {
    long duration = System.nanoTime() - startTime;
    long count = itemCount.get();
    double seconds = duration / 1_000_000_000.0;
    double rate = count / seconds;
    System.err.printf("Performance: %d values in %.3f seconds (%.0f/s)%n", count, seconds, rate);
  }

  static <T> Stream<T> wrapIfNeeded(PerformanceTracker tracker, Stream<T> stream) {
    return tracker != null ? tracker.trackStream(stream) : stream;
  }

  static void reportIfNeeded(PerformanceTracker tracker) {
    if (tracker != null) {
      tracker.printStats();
    }
  }
}
