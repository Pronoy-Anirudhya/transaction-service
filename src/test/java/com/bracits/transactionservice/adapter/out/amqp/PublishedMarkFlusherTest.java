package com.bracits.transactionservice.adapter.out.amqp;

import com.bracits.transactionservice.port.out.TxnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishedMarkFlusherTest {

  private static final int BATCH = 500;

  private final List<List<UUID>> statements = new CopyOnWriteArrayList<>();
  private final TxnRepository txnRepository = mock(TxnRepository.class);
  private final PublishedMarkBuffer buffer = new PublishedMarkBuffer();
  private final PublishedMarkFlusher flusher =
      new PublishedMarkFlusher(buffer, txnRepository, EventFixtures.properties(Duration.ofSeconds(5), BATCH));

  PublishedMarkFlusherTest() {
    when(txnRepository.markEventsPublished(any())).thenAnswer(invocation -> {
      Collection<UUID> ids = invocation.getArgument(0);
      statements.add(List.copyOf(ids));
      return ids.size();
    });
  }

  @Test
  void scheduledFlushWritesBatchesOfAtMost500() {
    List<UUID> ids = ids(1_200);
    ids.forEach(buffer::add);

    int handled = flusher.flush();

    assertThat(handled).isEqualTo(1_200);
    assertThat(statements).extracting(List::size).containsExactly(500, 500, 200);
    assertThat(statements.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(ids);
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  void emptyBufferWritesNothing() {
    flusher.scheduledFlush();

    assertThat(statements).isEmpty();
  }

  @Test
  void belowBatchSizeWaitsForTheScheduledFlush() throws InterruptedException {
    ids(BATCH - 1).forEach(flusher::onAck);

    TimeUnit.MILLISECONDS.sleep(200);
    assertThat(statements).isEmpty();

    flusher.scheduledFlush();
    assertThat(statements).extracting(List::size).containsExactly(BATCH - 1);
  }

  @Test
  void fullBatchIsFlushedEarlyWithoutTheScheduler() {
    List<UUID> ids = ids(BATCH);

    ids.forEach(flusher::onAck);

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(statements.stream().flatMap(List::stream).toList()).containsExactlyInAnyOrderElementsOf(ids));
    assertThat(statements).allSatisfy(statement -> assertThat(statement).hasSizeLessThanOrEqualTo(BATCH));
  }

  @Test
  void databaseErrorIsLoggedAndTheBatchDropped() {
    doThrow(new DataAccessResourceFailureException("PostgreSQL down"))
        .doAnswer(invocation -> {
          Collection<UUID> ids = invocation.getArgument(0);
          statements.add(List.copyOf(ids));
          return ids.size();
        })
        .when(txnRepository).markEventsPublished(any());
    ids(3).forEach(buffer::add);

    assertThat(flusher.flush()).isEqualTo(3);
    assertThat(buffer.isEmpty()).isTrue();
    assertThat(statements).isEmpty();

    UUID next = UUID.randomUUID();
    buffer.add(next);
    flusher.flush();
    assertThat(statements).containsExactly(List.of(next));
  }

  @Test
  void concurrentFlushIsSkippedWhileOneIsRunning() throws InterruptedException {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(invocation -> {
      inside.countDown();
      release.await(5, TimeUnit.SECONDS);
      return 1;
    }).when(txnRepository).markEventsPublished(any());
    buffer.add(UUID.randomUUID());
    AtomicBoolean done = new AtomicBoolean();
    Thread running = Thread.ofVirtual().start(() -> {
      flusher.flush();
      done.set(true);
    });
    assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
    buffer.add(UUID.randomUUID());

    assertThat(flusher.flush()).isZero();
    assertThat(buffer.size()).isEqualTo(1);

    release.countDown();
    running.join(Duration.ofSeconds(5));
    assertThat(done).isTrue();
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  void shutdownFlushesWhatIsLeft() {
    List<UUID> ids = ids(7);
    ids.forEach(flusher::onAck);

    flusher.flushOnShutdown();

    assertThat(statements).containsExactly(ids);
  }

  @Test
  void bufferDrainsOldestFirstAndTracksSize() {
    List<UUID> ids = ids(3);
    ids.forEach(buffer::add);

    assertThat(buffer.size()).isEqualTo(3);
    assertThat(buffer.drain(2)).containsExactly(ids.get(0), ids.get(1));
    assertThat(buffer.size()).isEqualTo(1);
    assertThat(buffer.drain(5)).containsExactly(ids.get(2));
    assertThat(buffer.drain(5)).isEmpty();
    assertThat(buffer.isEmpty()).isTrue();
  }

  private static List<UUID> ids(int count) {
    return new ArrayList<>(IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList());
  }
}
