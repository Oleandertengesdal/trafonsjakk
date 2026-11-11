package no.trafonsjakk.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import no.trafonsjakk.support.ApiSession.Response;

/** Fires several requests at the same instant, to provoke races. */
public final class Race {

    private Race() {
    }

    @SafeVarargs
    public static List<Response> run(Supplier<Response>... calls) {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch ready = new CountDownLatch(calls.length);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (Supplier<Response> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.get();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Response> out = new ArrayList<>();
            for (Future<Response> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    public static long count(List<Response> rs, int status) {
        return rs.stream().filter(r -> r.status() == status).count();
    }
}
