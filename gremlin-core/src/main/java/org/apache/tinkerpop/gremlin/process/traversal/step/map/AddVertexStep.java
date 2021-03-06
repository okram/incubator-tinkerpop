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

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.Mutating;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.Event;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.EventCallback;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public final class AddVertexStep<S> extends MapStep<S, Vertex> implements Mutating<EventCallback<Event.VertexAddedEvent>> {

    private final Object[] keyValues;
    private final transient Graph graph;

    private List<EventCallback<Event.VertexAddedEvent>> callbacks = null;

    public AddVertexStep(final Traversal.Admin traversal, final Object... keyValues) {
        super(traversal);
        this.keyValues = keyValues;
        this.graph = this.getTraversal().getGraph().get();
    }

    public Object[] getKeyValues() {
        return keyValues;
    }

    @Override
    protected Vertex map(final Traverser.Admin<S> traverser) {
        final Vertex v = this.graph.addVertex(this.keyValues);
        if (callbacks != null) {
            final Event.VertexAddedEvent vae = new Event.VertexAddedEvent(DetachedFactory.detach(v, true));
            callbacks.forEach(c -> c.accept(vae));
        }
        return v;
    }

    @Override
    public void addCallback(final EventCallback<Event.VertexAddedEvent> vertexAddedEventEventCallback) {
        if (callbacks == null) callbacks = new ArrayList<>();
        callbacks.add(vertexAddedEventEventCallback);
    }

    @Override
    public void removeCallback(final EventCallback<Event.VertexAddedEvent> vertexAddedEventEventCallback) {
        if (callbacks != null) callbacks.remove(vertexAddedEventEventCallback);
    }

    @Override
    public void clearCallbacks() {
        if (callbacks != null) callbacks.clear();
    }

    @Override
    public List<EventCallback<Event.VertexAddedEvent>> getCallbacks() {
        return (callbacks != null) ? Collections.unmodifiableList(callbacks) : Collections.emptyList();
    }
}
