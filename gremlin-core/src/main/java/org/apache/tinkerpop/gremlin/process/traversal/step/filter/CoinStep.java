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

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;

import java.util.Collections;
import java.util.Random;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CoinStep<S> extends FilterStep<S> {

    private static final Random RANDOM = new Random();
    private final double probability;

    public CoinStep(final Traversal.Admin traversal, final double probability) {
        super(traversal);
        this.probability = probability;
    }

    @Override
    protected boolean filter(final Traverser.Admin<S> traverser) {
        long newBulk = 0l;
        for (int i = 0; i < traverser.bulk(); i++) {
            if (this.probability >= RANDOM.nextDouble())
                newBulk++;
        }
        if (0 == newBulk) return false;
        traverser.setBulk(newBulk);
        return true;
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.probability);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return Collections.singleton(TraverserRequirement.BULK);
    }
}
