/**
 * Copyright 2009-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.futureconverter.springjava;

import org.junit.After;
import org.junit.Test;
import org.springframework.util.concurrent.ListenableFutureTask;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Thread.sleep;
import static net.javacrumbs.futureconverter.springjava.FutureConverter.toCompletableFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ToCompletableFutureConverterTest {

    public static final String VALUE = "test";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final CountDownLatch waitLatch = new CountDownLatch(1);

    private final CountDownLatch latch = new CountDownLatch(1);

    @After
    public void shutdown() {
        executorService.shutdown();
    }

    @Test
    public void testConvertToListenableCompleted() throws ExecutionException, InterruptedException {
        ListenableFutureTask<String> listenable = new ListenableFutureTask<>(() -> VALUE);
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        Consumer<String> consumer = mockConsumer();

        completable.thenAccept(consumer).thenRun(latch::countDown);

        assertEquals(VALUE, completable.get());
        assertEquals(true, completable.isDone());
        assertEquals(false, completable.isCancelled());
        latch.await();
        verify(consumer).accept(VALUE);

    }

    @SuppressWarnings("unchecked")
    private Consumer<String> mockConsumer() {
        return mock(Consumer.class);
    }

    @Test
    public void testRun() throws ExecutionException, InterruptedException {
        ListenableFutureTask<String> listenable = createRunningTask();
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        Consumer<String> consumer = mockConsumer();
        assertEquals(false, completable.isDone());
        assertEquals(false, completable.isCancelled());

        completable.thenAccept(consumer).thenRun(latch::countDown);
        waitLatch.countDown();

        //wait for the result
        assertEquals(VALUE, completable.get());
        assertEquals(true, completable.isDone());
        assertEquals(false, completable.isCancelled());

        latch.await();
        verify(consumer).accept(VALUE);
    }


    @Test
    public void testCancelOriginal() throws ExecutionException, InterruptedException {
        ListenableFutureTask<String> listenable = createRunningTask();
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        assertTrue(listenable.cancel(true));

        try {
            completable.get();
            fail("Exception expected");
        } catch (CancellationException e) {
            //ok
        }
        assertEquals(true, completable.isDone());
        assertEquals(true, completable.isCancelled());
        assertEquals(true, listenable.isDone());
        assertEquals(true, listenable.isCancelled());
    }

    @Test
    public void testCancelNew() throws ExecutionException, InterruptedException {
        ListenableFutureTask<String> listenable = createRunningTask();
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        assertTrue(completable.cancel(true));

        try {
            completable.get();
            fail("Exception expected");
        } catch (CancellationException e) {
            //ok
        }
        assertEquals(true, completable.isDone());
        assertEquals(true, completable.isCancelled());
        assertEquals(true, listenable.isDone());
        assertEquals(true, listenable.isCancelled());
    }

    private ListenableFutureTask<String> createRunningTask() throws InterruptedException {
        return new ListenableFutureTask<>(() -> {
            waitLatch.await();
            return VALUE;
        });
    }

    @Test
    public void testCancelCompleted() throws ExecutionException, InterruptedException {
        ListenableFutureTask<String> listenable = new ListenableFutureTask<>(() -> VALUE);
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        Consumer<String> consumer = mockConsumer();

        completable.thenAccept(consumer).thenRun(latch::countDown);

        assertEquals(VALUE, completable.get());
        assertEquals(true, completable.isDone());
        assertEquals(false, completable.isCancelled());
        latch.await();
        verify(consumer).accept(VALUE);

        assertFalse(completable.cancel(true));
    }

    @Test
    public void testConvertToCompletableException() throws ExecutionException, InterruptedException {
        doTestException(new RuntimeException("test"));
    }

//    @Test
//    public void testConvertToCompletableIOException() throws ExecutionException, InterruptedException {
//        Throwable exception = new IOException("test");
//        doTestException(exception);
//    }


    private void doTestException(RuntimeException exception) throws InterruptedException {
        ListenableFutureTask<String> listenable = new ListenableFutureTask<>(() -> {
            throw exception;
        });
        executorService.execute(listenable);

        CompletableFuture<String> completable = toCompletableFuture(listenable);
        Function<Throwable, ? extends String> fn = mockFunction();

        completable.exceptionally(fn).thenRun(latch::countDown);
        try {
            completable.get();
            fail("Exception expected");
        } catch (ExecutionException e) {
            assertEquals(exception, e.getCause());
        }
        assertEquals(true, completable.isDone());
        assertEquals(false, completable.isCancelled());

        latch.await();
        verify(fn).apply(exception);
    }

    @SuppressWarnings("unchecked")
    private Function<Throwable, ? extends String> mockFunction() {
        return mock(Function.class);
    }


    @Test
    public void testPools() throws InterruptedException {
        CountDownLatch waitLatch = new CountDownLatch(1);

        //Future<?> future = CompletableFuture.runAsync(() -> {
        Future<?> future = Executors.newWorkStealingPool(1).submit(() -> {
            try {
                System.out.println("Wait");
                waitLatch.await(); //cancel should interrupt
                System.out.println("Done");
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
                throw new RuntimeException(e);
            }
        });

        sleep(10); //give it some time to start (ugly, but works)
        future.cancel(true);
        System.out.println("Cancel called");

        assertTrue(future.isCancelled());

        assertTrue(future.isDone());
        sleep(100); //give it some time to finish
    }

}
