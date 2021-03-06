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
package org.apache.tinkerpop.gremlin.process.traversal.step.filter;

import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.process.AbstractGremlinProcessTest;
import org.apache.tinkerpop.gremlin.process.IgnoreEngine;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalEngine;
import org.apache.tinkerpop.gremlin.process.UseEngine;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public abstract class RetainTest extends AbstractGremlinProcessTest {

    public abstract Traversal<Vertex, Vertex> get_g_VX1X_out_retainXg_v2X(final Object v1Id, final Object v2Id);

    public abstract Traversal<Vertex, Vertex> get_g_VX1X_out_aggregateXxX_out_retainXxX(final Object v1Id);

    public abstract Traversal<Vertex, String> get_g_VX1X_asXaX_outXcreatedX_inXcreatedX_retainXaX_name(final Object v1Id);

    @Test
    @LoadGraphWith(MODERN)
    @IgnoreEngine(TraversalEngine.Type.COMPUTER)
    public void g_VX1X_out_retainXg_v2X() {
        final Traversal<Vertex, Vertex> traversal = get_g_VX1X_out_retainXg_v2X(convertToVertexId("marko"), convertToVertexId("vadas"));
        printTraversalForm(traversal);
        assertEquals("vadas", traversal.next().<String>value("name"));
        assertFalse(traversal.hasNext());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void g_VX1X_out_aggregateXxX_out_retainXxX() {
        final Traversal<Vertex, Vertex> traversal = get_g_VX1X_out_aggregateXxX_out_retainXxX(convertToVertexId("marko"));
        printTraversalForm(traversal);
        assertEquals("lop", traversal.next().<String>value("name"));
        assertFalse(traversal.hasNext());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void g_VX1X_asXaX_outXcreatedX_inXcreatedX_retainXaX_name() {
        final Traversal<Vertex, String> traversal = get_g_VX1X_asXaX_outXcreatedX_inXcreatedX_retainXaX_name(convertToVertexId("marko"));
        printTraversalForm(traversal);
        assertEquals("marko", traversal.next());
        assertFalse(traversal.hasNext());
    }

    @UseEngine(TraversalEngine.Type.STANDARD)
    @UseEngine(TraversalEngine.Type.COMPUTER)
    public static class Traversals extends RetainTest {

        @Override
        public Traversal<Vertex, Vertex> get_g_VX1X_out_retainXg_v2X(final Object v1Id, final Object v2Id) {
            return g.V(v1Id).out().retain(g.V(v2Id).next());
        }

        @Override
        public Traversal<Vertex, Vertex> get_g_VX1X_out_aggregateXxX_out_retainXxX(final Object v1Id) {
            return g.V(v1Id).out().aggregate("x").out().retain("x");
        }

        @Override
        public Traversal<Vertex, String> get_g_VX1X_asXaX_outXcreatedX_inXcreatedX_retainXaX_name(final Object v1Id) {
            return g.V(v1Id).as("a").out("created").in("created").retain("a").values("name");
        }
    }
}
