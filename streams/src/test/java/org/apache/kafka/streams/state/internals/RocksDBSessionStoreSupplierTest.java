/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.state.internals;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsMetrics;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.processor.internals.MockStreamsMetrics;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.test.MockProcessorContext;
import org.apache.kafka.test.NoOpRecordCollector;
import org.apache.kafka.test.TestUtils;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RocksDBSessionStoreSupplierTest {

    private static final String STORE_NAME = "name";
    private final ThreadCache cache = new ThreadCache("test", 1024, new MockStreamsMetrics(new Metrics()));
    private final MockProcessorContext context = new MockProcessorContext(null,
                                                                          TestUtils.tempDirectory(),
                                                                          Serdes.String(),
                                                                          Serdes.String(),
                                                                          new NoOpRecordCollector(),
                                                                          cache);

    private SessionStore<String, String> store;

    @After
    public void close() {
        store.close();
    }

    @Test
    public void shouldCreateLoggingEnabledStoreWhenStoreLogged() throws Exception {
        store = createStore(true, false);
        final List<ProducerRecord> logged = new ArrayList<>();
        final NoOpRecordCollector collector = new NoOpRecordCollector() {
            @Override
            public <K, V> void send(final ProducerRecord<K, V> record, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) {
                logged.add(record);
            }
        };
        final MockProcessorContext context = new MockProcessorContext(null,
                                                                      TestUtils.tempDirectory(),
                                                                      Serdes.String(),
                                                                      Serdes.String(),
                                                                      collector,
                                                                      cache);
        context.setTime(1);
        store.init(context, store);
        store.put(new Windowed<>("a", new SessionWindow(0, 10)), "b");
        assertFalse(logged.isEmpty());
    }

    @Test
    public void shouldNotBeLoggingEnabledStoreWhenLoggingNotEnabled() throws Exception {
        store = createStore(false, false);
        final List<ProducerRecord> logged = new ArrayList<>();
        final NoOpRecordCollector collector = new NoOpRecordCollector() {
            @Override
            public <K, V> void send(final ProducerRecord<K, V> record, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) {
                logged.add(record);
            }
        };
        final MockProcessorContext context = new MockProcessorContext(null,
                                                                      TestUtils.tempDirectory(),
                                                                      Serdes.String(),
                                                                      Serdes.String(),
                                                                      collector,
                                                                      cache);
        context.setTime(1);
        store.init(context, store);
        store.put(new Windowed<>("a", new SessionWindow(0, 10)), "b");
        assertTrue(logged.isEmpty());
    }

    @Test
    public void shouldReturnCachedSessionStoreWhenCachingEnabled() throws Exception {
        store = createStore(false, true);
        store.init(context, store);
        context.setTime(1);
        store.put(new Windowed<>("a", new SessionWindow(0, 10)), "b");
        store.put(new Windowed<>("b", new SessionWindow(0, 10)), "c");
        assertThat(store, is(instanceOf(CachingSessionStore.class)));
        assertThat(cache.size(), is(2L));
    }

    @Test
    public void shouldReturnRocksDbStoreWhenCachingAndLoggingDisabled() throws Exception {
        store = createStore(false, false);
        assertThat(store, is(instanceOf(RocksDBSessionStore.class)));
    }

    @Test
    public void shouldReturnRocksDbStoreWhenCachingDisabled() throws Exception {
        store = createStore(true, false);
        assertThat(store, is(instanceOf(RocksDBSessionStore.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldHaveMeteredStoreWhenCached() throws Exception {
        store = createStore(false, true);
        store.init(context, store);
        final StreamsMetrics metrics = context.metrics();
        assertFalse(metrics.metrics().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldHaveMeteredStoreWhenLogged() throws Exception {
        store = createStore(true, false);
        store.init(context, store);
        final StreamsMetrics metrics = context.metrics();
        assertFalse(metrics.metrics().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldHaveMeteredStoreWhenNotLoggedOrCached() throws Exception {
        store = createStore(false, false);
        store.init(context, store);
        final StreamsMetrics metrics = context.metrics();
        assertFalse(metrics.metrics().isEmpty());
    }



    private SessionStore<String, String> createStore(final boolean logged, final boolean cached) {
        return new RocksDBSessionStoreSupplier<>(STORE_NAME,
                                                 10,
                                                 Serdes.String(),
                                                 Serdes.String(),
                                                 logged,
                                                 Collections.<String, String>emptyMap(),
                                                 cached).get();
    }

}