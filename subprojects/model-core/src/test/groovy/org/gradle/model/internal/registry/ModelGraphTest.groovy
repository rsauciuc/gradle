/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.ModelNode.State
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelPromise
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class ModelGraphTest extends Specification {
    def graph = new ModelGraph(root())
    def nodes = [:]

    def setup() {
        nodes[graph.root.path] = graph.root
    }

    def "notifies listener when node added"() {
        def listener = Mock(ModelListener)
        def a = node("a")
        def b = node("b")

        given:
        graph.addListener(listener)

        when:
        graph.add(a)
        graph.add(b)

        then:
        1 * listener.onDiscovered(a)
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener when node added in projected state"() {
        def listener = Mock(ModelListener)
        def a = node("a")
        def b = node("b")

        given:
        graph.addListener(listener)

        when:
        graph.add(a)

        then:
        1 * listener.onDiscovered(a)
        0 * listener.onDiscovered(_)

        when:
        graph.add(b)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of existing nodes"() {
        def listener = Mock(ModelListener)
        def a = node("a")
        def b = node("b")

        given:
        graph.add(a)
        graph.add(b)

        when:
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(graph.root)
        1 * listener.onDiscovered(a)
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener when node reaches projected state"() {
        def listener = Mock(ModelListener)
        def a = node("a", String, State.Registered)

        given:
        graph.addListener(listener)

        when:
        graph.add(a)

        then:
        0 * listener.onDiscovered(_)

        when:
        graph.nodeDiscovered(a)

        then:
        1 * listener.onDiscovered(a)
        0 * listener.onDiscovered(_)
    }

    def "stops notifying listener of new nodes after listener signals it is done"() {
        def listener = Mock(ModelListener)
        def a = node("a")
        def b = node("b")

        given:
        graph.addListener(listener)

        when:
        graph.add(a)
        graph.add(b)

        then:
        1 * listener.onDiscovered(a) >> true
        0 * listener.onDiscovered(_)
    }

    def "stops notifying listener of existing nodes after listener signals it is done"() {
        def listener = Mock(ModelListener)
        def a = node("a")
        def b = node("b")
        def c = node("c")

        given:
        graph.add(a)
        graph.add(b)

        when:
        graph.addListener(listener)
        graph.add(c)

        then:
        1 * listener.onDiscovered(graph.root)
        1 * listener.onDiscovered(a) >> true
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of new node with matching path"() {
        def listener = Mock(ModelListener)

        def a = node("a")
        def b = node("b")
        def c = node("c")

        given:
        listener.getPath() >> b.path
        graph.addListener(listener)

        when:
        graph.add(a)
        graph.add(b)
        graph.add(c)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of existing node with matching path"() {
        def listener = Mock(ModelListener)

        def a = node("a")
        def b = node("b")
        def c = node("c")

        given:
        listener.getPath() >> b.path
        graph.add(a)
        graph.add(b)
        graph.add(c)

        when:
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of node with matching parent"() {
        def listener = Mock(ModelListener)

        def a = node("a")
        def b = node("a.b")
        def c = node("a.c")
        def d = node("d")

        given:
        listener.getParent() >> a.path
        a.links >> [b]

        when:
        graph.add(a)
        graph.add(b)
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)

        when:
        graph.add(c)
        graph.add(d)

        then:
        1 * listener.onDiscovered(c)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of node with matching type"() {
        def listener = Mock(ModelListener)

        def a = node("a", Integer)
        def b = node("b", String)
        def c = node("c", String)
        def d = node("d", Long)

        given:
        listener.getType() >> ModelType.of(String)

        when:
        graph.add(a)
        graph.add(b)

        then:
        0 * listener.onDiscovered(_)

        when:
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)

        when:
        graph.add(c)

        then:
        1 * listener.onDiscovered(c)
        0 * listener.onDiscovered(_)

        when:
        graph.add(d)

        then:
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of node with matching parent and type"() {
        def listener = Mock(ModelListener)

        def a = node("a", String)
        def b = node("a.b", String)
        def c = node("a.c", String)
        def d = node("a.d", Long)

        given:
        listener.getType() >> ModelType.of(String)
        listener.getParent() >> a.path
        a.links >> [b]

        when:
        graph.add(a)
        graph.add(b)
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)

        when:
        graph.add(c)
        graph.add(d)

        then:
        1 * listener.onDiscovered(c)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of node with matching ancestor"() {
        def listener = Mock(ModelListener)

        def a = node("a")
        def b = node("a.b")
        def c = node("a.b.c")
        def d = node("a.b.d")
        def e = node("a.b.c.e")
        def f = node("d")

        given:
        listener.ancestor >> a.path
        a.links >> [b]
        b.links >> [c]

        when:
        graph.add(a)
        graph.add(b)
        graph.add(c)
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(b)
        1 * listener.onDiscovered(c)
        0 * listener.onDiscovered(_)

        when:
        graph.add(d)
        graph.add(e)
        graph.add(f)

        then:
        1 * listener.onDiscovered(d)
        1 * listener.onDiscovered(e)
        0 * listener.onDiscovered(_)
    }

    def "notifies listener of node with root ancestor"() {
        def listener = Mock(ModelListener)

        def a = node("a")
        def b = node("a.b")
        def c = node("a.b.c")
        def d = node("d")

        given:
        listener.ancestor >> ModelPath.ROOT

        when:
        graph.add(a)
        graph.add(b)
        graph.addListener(listener)

        then:
        1 * listener.onDiscovered(graph.root)
        1 * listener.onDiscovered(a)
        1 * listener.onDiscovered(b)
        0 * listener.onDiscovered(_)

        when:
        graph.add(c)
        graph.add(d)

        then:
        1 * listener.onDiscovered(c)
        1 * listener.onDiscovered(d)
        0 * listener.onDiscovered(_)
    }

    def "listener can add listeners when node added"() {
        def listener1 = Mock(ModelListener)
        def listener2 = Mock(ModelListener)
        def listener3 = Mock(ModelListener)
        def a = node("a")
        def b = node("b")

        given:
        listener2.getPath() >> b.path
        listener3.getPath() >> b.path

        when:
        graph.add(a)
        graph.addListener(listener1)
        graph.add(b)

        then:
        1 * listener1.onDiscovered(graph.root)
        1 * listener1.onDiscovered(a) >> { graph.addListener(listener2); false }
        1 * listener1.onDiscovered(b) >> { graph.addListener(listener3); false }
        1 * listener2.onDiscovered(b)
        1 * listener3.onDiscovered(b)
        0 * listener1.onDiscovered(_)
        0 * listener2.onDiscovered(_)
        0 * listener3.onDiscovered(_)
    }

    def "listener can add nodes that are consumed by other listeners"() {
        def listener1 = Mock(ModelListener)
        def listener2 = Mock(ModelListener)
        def a = node("a")
        def b = node("b")
        def c = node("c")
        def d = node("d")

        given:
        graph.addListener(listener1)
        listener2.getPath() >> b.path
        graph.addListener(listener2)

        when:
        graph.add(a)

        then:
        1 * listener1.onDiscovered(a) >> { graph.add(b); false }
        1 * listener1.onDiscovered(b) >> { graph.add(c); false }
        1 * listener1.onDiscovered(c) >> { graph.add(d); false }
        1 * listener1.onDiscovered(d)
        1 * listener2.onDiscovered(b) >> true
        0 * listener1.onDiscovered(_)
        0 * listener2.onDiscovered(_)
    }

    def "listener is not notified of nodes it creates after it signals it is done"() {
        def listener1 = Mock(ModelListener)
        def listener2 = Mock(ModelListener)
        def a = node("a")
        def b = node("b")
        def c = node("c")
        def d = node("d")

        given:
        graph.addListener(listener1)
        graph.addListener(listener2)
        listener2.onDiscovered(b) >> { graph.add(c); graph.add(d) }

        when:
        graph.add(a)

        then:
        1 * listener1.onDiscovered(a) >> {
            graph.add(b);
            true
        }
        0 * listener1.onDiscovered(_)
    }

    def node(String path, Class<?> type = String, State state = State.Discovered) {
        return Stub(ModelNodeInternal) {
            getPath() >> ModelPath.path(path)
            getState() >> state
            isAtLeast(_) >> { target -> state.isAtLeast(target) }
            getPromise() >> Stub(ModelPromise) {
                canBeViewedAsMutable(_) >> { ModelType t -> return t.concreteClass == type }
            }
            toString() >> "node $path"
        }
    }

    def root() {
        return Stub(ModelNodeInternal) {
            getPath() >> ModelPath.ROOT
        }
    }
}
