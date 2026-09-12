package com.mcpgateway.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a running command has printed so far, for as long as somebody is watching.
 *
 * <p>Held here rather than written down, and that is the whole design. A run's output is
 * stored once, sealed, when the executor reports what happened; this is the same output
 * arriving a second at a time while the command is still going, and persisting it as well
 * would mean production data in two places, one of them re-sealed thirty times a minute
 * for a screen nobody may still be looking at.
 *
 * <p>So a live view is exactly that: it lasts as long as the run and a little after, it is
 * bounded, and when the run finishes the stored output takes over. Nothing here is a
 * record — {@code run_targets} is.
 *
 * <p>One process holds one run's view. With more than one gateway behind a load balancer a
 * poll could reach an instance that never saw the chunks; the run still completes and its
 * stored output is correct, so the failure is a live view that stays empty rather than
 * anything lost. A shared cache is where that would go if it ever matters.
 */
@Slf4j
@Component
public class RunProgress {

    /** Per host, so a rolling command across three servers reads as three logs. */
    private static final int PER_HOST_LIMIT = 64 * 1024;

    /** How long a finished run's view stays before it is dropped. */
    private static final Duration LINGER = Duration.ofMinutes(2);

    /** A ceiling on runs held at once, in case something stops finishing them. */
    private static final int MAX_RUNS = 200;

    private final Map<String, Watched> byRun = new ConcurrentHashMap<>();

    /** Appends a chunk to the run's live view. */
    public void append(String runRef, String host, String stdout, String stderr, boolean done) {
        if (runRef == null || runRef.isBlank()) {
            return;
        }

        evictStale();

        Watched watched = byRun.computeIfAbsent(runRef, ignored -> new Watched());
        watched.touch();
        watched.hosts.computeIfAbsent(host == null ? "" : host, ignored -> new Tail())
                .add(stdout, stderr, done);
    }

    /** What has arrived for this run, per host, oldest first. Empty when nothing has. */
    public List<Live> of(String runRef) {
        Watched watched = runRef == null ? null : byRun.get(runRef);

        if (watched == null) {
            return List.of();
        }

        List<Live> live = new ArrayList<>();
        watched.hosts.forEach((host, tail) ->
                live.add(new Live(host, tail.out.toString(), tail.err.toString(), tail.done)));

        return live;
    }

    /**
     * Drops a run's view once its result has been recorded.
     *
     * <p>Not immediately: the poll that shows the last lines usually arrives after the
     * result does, and clearing on the instant would blank the screen at the moment the
     * command finished. It lingers, then goes.
     */
    public void finished(String runRef) {
        Watched watched = runRef == null ? null : byRun.get(runRef);

        if (watched != null) {
            watched.endedAt = Instant.now();
        }
    }

    private void evictStale() {
        Instant cutoff = Instant.now().minus(LINGER);

        byRun.entrySet().removeIf(entry ->
                entry.getValue().endedAt != null && entry.getValue().endedAt.isBefore(cutoff));

        if (byRun.size() <= MAX_RUNS) {
            return;
        }

        // Something is not finishing runs. Dropping the least recently touched keeps this
        // bounded; the runs themselves are unaffected, they simply stop being watchable.
        byRun.entrySet().stream()
                .sorted((a, b) -> a.getValue().touchedAt.compareTo(b.getValue().touchedAt))
                .limit(byRun.size() - MAX_RUNS)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(byRun::remove);

        log.debug("Dropped the oldest live views; {} run(s) are being watched", byRun.size());
    }

    /** One host's live output. */
    public record Live(String host, String stdout, String stderr, boolean done) {
    }

    private static final class Watched {
        private final Map<String, Tail> hosts = new LinkedHashMap<>();
        private volatile Instant touchedAt = Instant.now();
        private volatile Instant endedAt;

        private void touch() {
            touchedAt = Instant.now();
        }
    }

    /**
     * One host's output, capped, keeping the newest.
     *
     * <p>The newest rather than the oldest: this is a log somebody is watching, and the
     * lines they want are the ones that just arrived. The stored output, which keeps the
     * beginning, is the record.
     */
    private static final class Tail {
        private final StringBuilder out = new StringBuilder();
        private final StringBuilder err = new StringBuilder();
        private volatile boolean done;

        private synchronized void add(String stdout, String stderr, boolean ended) {
            append(out, stdout);
            append(err, stderr);
            if (ended) {
                done = true;
            }
        }

        private static void append(StringBuilder buffer, String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

            buffer.append(chunk);

            if (buffer.length() > PER_HOST_LIMIT) {
                buffer.delete(0, buffer.length() - PER_HOST_LIMIT);
            }
        }
    }
}
