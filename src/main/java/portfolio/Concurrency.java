package portfolio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The app's single, shared thread pool.
 *
 * This is the "thread pool" half of the concurrency requirement: instead of
 * spinning up a brand new Thread every time something needs to talk to the
 * database or copy a file (which is plain multi-threading but wasteful), a
 * small fixed number of worker threads are created once here and reused for
 * every background job, submitted via pool().submit(...).
 *
 * The "multi-threading" half is in PortfolioApp.runBackground(...), which
 * wraps each job in a javafx.concurrent.Task and hands it to this pool, so the
 * actual work (SQL, file I/O) runs on one of these worker threads instead of
 * the JavaFX Application Thread.
 *
 * Where it is used, concretely:
 *   - PortfolioApp.loadDataInBackground()      -> reading the database at startup
 *   - PortfolioApp.showAddDialog()             -> saving a new item (+ attachment copy)
 *   - itemCard(...)'s Remove button            -> deleting an item
 *   - buildCommentsSection(...)'s Post button  -> saving a comment
 *   - commentCard(...)'s Delete button         -> deleting a comment
 *   - chooseAndAttach(...)                     -> replacing an item's attachment
 *
 * Threads are marked daemon so a leftover background job never keeps the JVM
 * alive after the window is closed.
 */
public final class Concurrency {

    /** Four workers is plenty for a single-user desktop app; tune if needed. */
    private static final int POOL_SIZE = 4;

    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(POOL_SIZE, daemonThreadFactory());

    private Concurrency() {
    }

    public static ExecutorService pool() {
        return POOL;
    }

    /** Called from PortfolioApp.stop() so the app can exit cleanly. */
    public static void shutdown() {
        POOL.shutdown();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "portfolio-worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}