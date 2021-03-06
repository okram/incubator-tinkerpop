/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.map;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.computer.KeyValue;
import org.apache.tinkerpop.gremlin.process.computer.MapReduce;
import org.apache.tinkerpop.gremlin.process.computer.traversal.TraversalVertexProgram;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ReducingBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMatrix;
import org.apache.tinkerpop.gremlin.process.traversal.step.MapReducer;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalUtil;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.BulkSet;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.util.TraverserSet;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GroupStep<S, K, V, R> extends ReducingBarrierStep<S, Map<K, R>> implements MapReducer, TraversalParent {

    private char state = 'k';

    private Traversal.Admin<S, K> keyTraversal = null;
    private Traversal.Admin<S, V> valueTraversal = null;
    private Traversal.Admin<Collection<V>, R> reduceTraversal = null;

    public GroupStep(final Traversal.Admin traversal) {
        super(traversal);
        this.setSeedSupplier((Supplier) new GroupMapSupplier());
        this.setBiFunction((BiFunction) new GroupBiFunction());
    }

    @Override
    public <A, B> List<Traversal.Admin<A, B>> getLocalChildren() {
        final List<Traversal.Admin<A, B>> children = new ArrayList<>(3);
        if (null != this.keyTraversal)
            children.add((Traversal.Admin) this.keyTraversal);
        if (null != this.valueTraversal)
            children.add((Traversal.Admin) this.valueTraversal);
        if (null != this.reduceTraversal)
            children.add((Traversal.Admin) this.reduceTraversal);
        return children;
    }

    public Traversal.Admin<Collection<V>, R> getReduceTraversal() {
        return this.reduceTraversal;
    }

    @Override
    public void addLocalChild(final Traversal.Admin<?, ?> kvrTraversal) {
        if ('k' == this.state) {
            this.keyTraversal = this.integrateChild(kvrTraversal);
            this.state = 'v';
        } else if ('v' == this.state) {
            this.valueTraversal = this.integrateChild(kvrTraversal);
            this.state = 'r';
        } else if ('r' == this.state) {
            this.reduceTraversal = this.integrateChild(kvrTraversal);
            this.state = 'x';
        } else {
            throw new IllegalStateException("The key, value, and reduce functions for group()-step have already been set");
        }
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.getSelfAndChildRequirements(TraverserRequirement.SIDE_EFFECTS, TraverserRequirement.BULK);
    }

    @Override
    public GroupStep<S, K, V, R> clone() {
        final GroupStep<S, K, V, R> clone = (GroupStep<S, K, V, R>) super.clone();
        if (null != this.keyTraversal)
            clone.keyTraversal = clone.integrateChild(this.keyTraversal.clone());
        if (null != this.valueTraversal)
            clone.valueTraversal = clone.integrateChild(this.valueTraversal.clone());
        if (null != this.reduceTraversal)
            clone.reduceTraversal = clone.integrateChild(this.reduceTraversal.clone());
        return clone;
    }

    @Override
    public MapReduce<K, Collection<V>, K, R, Map<K, R>> getMapReduce() {
        return new GroupMapReduce<>(this);
    }

    @Override
    public Traverser<Map<K, R>> processNextStart() {
        if (this.byPass) {
            final Traverser.Admin<S> traverser = this.starts.next();
            final Object[] kvPair = new Object[]{TraversalUtil.applyNullable(traverser, (Traversal.Admin<S, Map>) this.keyTraversal), TraversalUtil.applyNullable(traverser, (Traversal.Admin<S, Map>) this.valueTraversal)};
            return traverser.asAdmin().split(kvPair, (Step) this);
        } else {
            return super.processNextStart();
        }
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.keyTraversal, this.valueTraversal, this.reduceTraversal);
    }

    ///////////

    private class GroupBiFunction implements BiFunction<Map<K, Collection<V>>, Traverser.Admin<S>, Map<K, Collection<V>>>, Serializable {

        private GroupBiFunction() {

        }

        @Override
        public Map<K, Collection<V>> apply(final Map<K, Collection<V>> mutatingSeed, final Traverser.Admin<S> traverser) {
            final K key = TraversalUtil.applyNullable(traverser, GroupStep.this.keyTraversal);
            final V value = TraversalUtil.applyNullable(traverser, GroupStep.this.valueTraversal);
            Collection<V> values = mutatingSeed.get(key);
            if (null == values) {
                values = new BulkSet<>();
                mutatingSeed.put(key, values);
            }
            TraversalHelper.addToCollectionUnrollIterator(values, value, traverser.bulk());
            return mutatingSeed;
        }
    }

    //////////

    private class GroupMap extends HashMap<K, Collection<V>> implements FinalGet<Map<K, R>> {

        @Override
        public Map<K, R> getFinal() {
            if (null == GroupStep.this.reduceTraversal)
                return (Map<K, R>) this;
            else {
                final Map<K, R> reduceMap = new HashMap<>();
                this.forEach((k, vv) -> reduceMap.put(k, TraversalUtil.applyNullable(vv, GroupStep.this.reduceTraversal)));
                return reduceMap;
            }
        }
    }

    private class GroupMapSupplier implements Supplier<GroupMap>, Serializable {

        private GroupMapSupplier() {
        }

        @Override
        public GroupMap get() {
            return new GroupMap();
        }
    }

    ///////////

    public static final class GroupMapReduce<K, V, R> implements MapReduce<K, Collection<V>, K, R, Map<K, R>> {

        public static final String GROUP_BY_STEP_STEP_ID = "gremlin.groupStep.stepId";

        private String groupStepId;
        private Traversal.Admin<Collection<V>, R> reduceTraversal;

        private GroupMapReduce() {

        }

        public GroupMapReduce(final GroupStep<?, K, V, R> step) {
            this.groupStepId = step.getId();
            this.reduceTraversal = step.getReduceTraversal();
        }

        @Override
        public void storeState(final Configuration configuration) {
            MapReduce.super.storeState(configuration);
            configuration.setProperty(GROUP_BY_STEP_STEP_ID, this.groupStepId);
        }

        @Override
        public void loadState(final Configuration configuration) {
            this.groupStepId = configuration.getString(GROUP_BY_STEP_STEP_ID);
            final Traversal.Admin<?, ?> traversal = TraversalVertexProgram.getTraversalSupplier(configuration).get();
            if (!traversal.isLocked())
                traversal.applyStrategies(); // TODO: this is a scary error prone requirement, but only a problem for GroupStep
            final GroupStep groupStep = new TraversalMatrix<>(traversal).getStepById(this.groupStepId);
            this.reduceTraversal = groupStep.getReduceTraversal();
        }

        @Override
        public boolean doStage(final Stage stage) {
            return !stage.equals(Stage.COMBINE);
        }

        @Override
        public void map(final Vertex vertex, final MapEmitter<K, Collection<V>> emitter) {
            vertex.<TraverserSet<Object[]>>property(TraversalVertexProgram.HALTED_TRAVERSERS).ifPresent(traverserSet -> traverserSet.forEach(traverser -> {
                final Object[] objects = traverser.get();
                if (objects[1] instanceof Collection)
                    emitter.emit((K) objects[0], (Collection<V>) objects[1]);
                else {
                    final List<V> collection = new ArrayList<>();
                    collection.add((V) objects[1]);
                    emitter.emit((K) objects[0], collection);
                }
            }));
        }

        @Override
        public void reduce(final K key, final Iterator<Collection<V>> values, final ReduceEmitter<K, R> emitter) {
            final Set<V> set = new BulkSet<>();
            values.forEachRemaining(set::addAll);
            emitter.emit(key, TraversalUtil.applyNullable(set, this.reduceTraversal));
        }

        @Override
        public Map<K, R> generateFinalResult(final Iterator<KeyValue<K, R>> keyValues) {
            final Map<K, R> map = new HashMap<>();
            keyValues.forEachRemaining(keyValue -> map.put(keyValue.getKey(), keyValue.getValue()));
            return map;
        }

        @Override
        public String getMemoryKey() {
            return REDUCING;
        }

        @Override
        public GroupMapReduce<K, V, R> clone() {
            try {
                final GroupMapReduce<K, V, R> clone = (GroupMapReduce<K, V, R>) super.clone();
                if (null != clone.reduceTraversal)
                    clone.reduceTraversal = this.reduceTraversal.clone();
                return clone;
            } catch (final CloneNotSupportedException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        @Override
        public String toString() {
            return StringFactory.mapReduceString(this, this.getMemoryKey());
        }
    }

}