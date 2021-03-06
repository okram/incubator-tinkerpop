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
package org.apache.tinkerpop.gremlin.tinkergraph.process.traversal.step.sideEffect;

import org.apache.tinkerpop.gremlin.process.traversal.step.HasContainerHolder;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Compare;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Pieter Martin
 */
public class TinkerGraphStep<S extends Element> extends GraphStep<S> implements HasContainerHolder {

    public final List<HasContainer> hasContainers = new ArrayList<>();

    public TinkerGraphStep(final GraphStep<S> originalGraphStep) {
        super(originalGraphStep.getTraversal(), originalGraphStep.getReturnClass(), originalGraphStep.getIds());
        originalGraphStep.getLabels().forEach(this::addLabel);
        //No need to do anything if the first element is an Element, all elements are guaranteed to be an element and will be return as is
        if ((this.ids.length == 0 || !(this.ids[0] instanceof Element)))
            this.setIteratorSupplier(() -> (Iterator<S>) (Vertex.class.isAssignableFrom(this.returnClass) ? this.vertices() : this.edges()));
    }

    private Iterator<? extends Edge> edges() {
        final TinkerGraph graph = (TinkerGraph) this.getTraversal().getGraph().get();
        final HasContainer indexedContainer = getIndexKey(Edge.class);
        // ids are present, filter on them first
        if (this.ids != null && this.ids.length > 0)
            return this.iteratorList(graph.edges(this.ids));
        else
            return null == indexedContainer ?
                    this.iteratorList(graph.edges()) :
                    TinkerHelper.queryEdgeIndex(graph, indexedContainer.key, indexedContainer.value).stream()
                            .filter(edge -> HasContainer.testAll(edge, this.hasContainers))
                            .collect(Collectors.<Edge>toList()).iterator();
    }

    private Iterator<? extends Vertex> vertices() {
        final TinkerGraph graph = (TinkerGraph) this.getTraversal().getGraph().get();
        final HasContainer indexedContainer = getIndexKey(Vertex.class);
        // ids are present, filter on them first
        if (this.ids != null && this.ids.length > 0)
            return this.iteratorList(graph.vertices(this.ids));
        else
            return null == indexedContainer ?
                    this.iteratorList(graph.vertices()) :
                    TinkerHelper.queryVertexIndex(graph, indexedContainer.key, indexedContainer.value).stream()
                            .filter(vertex -> HasContainer.testAll(vertex, this.hasContainers))
                            .collect(Collectors.<Vertex>toList()).iterator();
    }

    private HasContainer getIndexKey(final Class<? extends Element> indexedClass) {
        final Set<String> indexedKeys = ((TinkerGraph) this.getTraversal().getGraph().get()).getIndexedKeys(indexedClass);
        return this.hasContainers.stream()
                .filter(c -> indexedKeys.contains(c.key) && c.predicate.equals(Compare.eq))
                .findAny()
                .orElseGet(() -> null);
    }

    public String toString() {
        if (this.hasContainers.isEmpty())
            return super.toString();
        else
            return 0 == this.ids.length ?
                    TraversalHelper.makeStepString(this, this.returnClass.getSimpleName().toLowerCase(), this.hasContainers) :
                    TraversalHelper.makeStepString(this, this.returnClass.getSimpleName().toLowerCase(), Arrays.toString(this.ids), this.hasContainers);
    }

    private final <E extends Element> Iterator<E> iteratorList(final Iterator<E> iterator) {
        final List<E> list = new ArrayList<>();
        while (iterator.hasNext()) {
            final E e = iterator.next();
            if (HasContainer.testAll(e, this.hasContainers))
                list.add(e);
        }
        return list.iterator();
    }

    @Override
    public List<HasContainer> getHasContainers() {
        return this.hasContainers;
    }

    @Override
    public void addHasContainer(final HasContainer hasContainer) {
        this.hasContainers.add(hasContainer);
    }
}
