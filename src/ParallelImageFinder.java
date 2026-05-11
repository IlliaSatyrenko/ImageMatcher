import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ParallelImageFinder<T> extends AbstractImageFinder<T> {
    private final int threadCount;
    private final int ioPoolThreshold;
    private static int cpuPoolThreshold;

    public ParallelImageFinder(
            ImageHashingStrategy hashingStrategy,
            ColorFilteringStrategy<T> colorStrategy,
            int threadCount
    ) {
        super(hashingStrategy, colorStrategy);
        this.threadCount = threadCount;

        ioPoolThreshold = threadCount * 5;
    }

    @Override
    public List<ImageMatch> findSimilarImages(List<Path> allImages) throws InterruptedException {
        // Етап 1: Паралельний Map (createSignature)
        MemoryGuard memoryGuard = new MemoryGuard(ioPoolThreshold);

        ForkJoinPool customCpuPool = new ForkJoinPool(threadCount);
        ExecutorService ioPool = Executors.newVirtualThreadPerTaskExecutor();

        ConcurrentLinkedQueue<ImageSignature<T>> signaturesQueue = new ConcurrentLinkedQueue<>();
        CountDownLatch mapPhaseLatch = new CountDownLatch(allImages.size());

        for (Path path : allImages) {
            ioPool.submit(() -> {
                try {
                    memoryGuard.acquireSpace();

                    BufferedImage img = loadImage(path);

                    if (img == null) {
                        memoryGuard.releaseSpace();
                        mapPhaseLatch.countDown();
                        return;
                    }

                    CompletableFuture.supplyAsync(() -> createSignature(path, img), customCpuPool)
                            .thenAccept(signaturesQueue::add)
                            .whenComplete((res, ex) -> {
                                memoryGuard.releaseSpace();
                                mapPhaseLatch.countDown();
                            });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    mapPhaseLatch.countDown();
                }
            });
        }

        mapPhaseLatch.await();
        ioPool.shutdown();

        // Етап 2: Паралельний Reduce (compareSignatures)
        List<ImageSignature<T>> allSignatures = new ArrayList<>(signaturesQueue);
        ConcurrentLinkedQueue<ImageMatch> matchesQueue = new ConcurrentLinkedQueue<>();

        cpuPoolThreshold = allSignatures.size() / (threadCount * 2) + 1;

        CompareTask rootTask = new CompareTask(allSignatures, 0, allSignatures.size(), matchesQueue);
        customCpuPool.invoke(rootTask);
        customCpuPool.shutdown();

        return new ArrayList<>(matchesQueue);
    }

    private static class MemoryGuard {
        private final int maxItems;
        private int currentItems = 0;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();

        public MemoryGuard(int maxItems) { this.maxItems = maxItems; }

        public void acquireSpace() throws InterruptedException {
            lock.lock();
            try {
                while (currentItems >= maxItems) {
                    notFull.await();
                }
                currentItems++;
            } finally {
                lock.unlock();
            }
        }

        public void releaseSpace() {
            lock.lock();
            try {
                currentItems--;
                notFull.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    private class CompareTask extends RecursiveAction {
        private final List<ImageSignature<T>> data;
        private final int startIdx, endIdx;
        private final ConcurrentLinkedQueue<ImageMatch> results;

        public CompareTask(
                List<ImageSignature<T>> data,
                int startIdx,
                int endIdx,
                ConcurrentLinkedQueue<ImageMatch> results
        ) {
            this.data = data;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.results = results;
        }

        @Override
        protected void compute() {
            int length = endIdx - startIdx;
            if (length <= cpuPoolThreshold) {
                computeDirectly();
                return;
            }

            int mid = startIdx + length / 2;

            CompareTask leftTask = new CompareTask(data, startIdx, mid, results);
            CompareTask rightTask = new CompareTask(data, mid, endIdx, results);

            invokeAll(leftTask, rightTask);
        }

        private void computeDirectly() {
            for (int i = startIdx; i < endIdx; i++) {
                ImageSignature<T> sig1 = data.get(i);

                for (int j = i + 1; j < data.size(); j++) {
                    ImageSignature<T> sig2 = data.get(j);

                    ImageMatch match = compareSignatures(sig1, sig2);

                    if (match != null) {
                        results.add(match);
                    }
                }
            }
        }
    }
}