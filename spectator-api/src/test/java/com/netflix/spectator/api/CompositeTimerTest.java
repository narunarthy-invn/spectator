/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CompositeTimerTest {

  private final ManualClock clock = new ManualClock();

  private Timer newTimer(int n) {
    Timer[] ms = new Timer[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = new DefaultTimer(clock, new DefaultId("foo"));
    }
    return new CompositeTimer(new DefaultId("foo"), clock, ms);
  }

  private void assertCountEquals(Timer t, long expected) {
    Assert.assertEquals(t.count(), expected);
    for (Meter m : ((CompositeTimer) t).meters()) {
      Assert.assertEquals(((Timer) m).count(), expected);
    }
  }

  private void assertTotalEquals(Timer t, long expected) {
    Assert.assertEquals(t.totalTime(), expected);
    for (Meter m : ((CompositeTimer) t).meters()) {
      Assert.assertEquals(((Timer) m).totalTime(), expected);
    }
  }

  @Test
  public void empty() {
    Timer t = new CompositeTimer(NoopId.INSTANCE, clock, new Timer[] {});
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
    t.record(1L, TimeUnit.SECONDS);
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testInit() {
    Timer t = newTimer(5);
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testRecord() {
    Timer t = newTimer(5);
    t.record(42, TimeUnit.MILLISECONDS);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 42000000L);
  }

  @Test
  public void testRecordCallable() throws Exception {
    Timer t = newTimer(5);
    clock.setMonotonicTime(100L);
    int v = t.record(new Callable<Integer>() {
      public Integer call() throws Exception {
        clock.setMonotonicTime(500L);
        return 42;
      }
    });
    Assert.assertEquals(v, 42);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 400L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer t = newTimer(5);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.record(new Callable<Integer>() {
        public Integer call() throws Exception {
          clock.setMonotonicTime(500L);
          throw new RuntimeException("foo");
        }
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 400L);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer t = newTimer(5);
    clock.setMonotonicTime(100L);
    t.record(new Runnable() {
      public void run() {
        clock.setMonotonicTime(500L);
      }
    });
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 400L);
  }

  @Test
  public void testRecordRunnableException() throws Exception {
    Timer t = newTimer(5);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.record(new Runnable() {
        public void run() {
          clock.setMonotonicTime(500L);
          throw new RuntimeException("foo");
        }
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 400L);
  }

  @Test
  public void testMeasure() {
    Timer t = newTimer(5);
    t.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag("statistic", "count"))) {
        Assert.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag("statistic", "totalTime"))) {
        Assert.assertEquals(m.value(), 42e6, 0.1e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }
}
