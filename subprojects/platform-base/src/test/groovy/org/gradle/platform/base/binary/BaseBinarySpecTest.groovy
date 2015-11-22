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

package org.gradle.platform.base.binary

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ModelInstantiationException
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class BaseBinarySpecTest extends Specification {
    def "cannot instantiate directly"() {
        when:
        new BaseBinarySpec() {}

        then:
        def e = thrown ModelInstantiationException
        e.message == "Direct instantiation of a BaseBinarySpec is not permitted. Use a BinaryTypeBuilder instead."
    }

    def "binary has name and sensible display name"() {
        def binary = create(SampleBinary, MySampleBinary, "sampleBinary")

        expect:
        binary instanceof MySampleBinary
        binary.name == "sampleBinary"
        binary.projectScopedName == "sampleBinary"
        binary.displayName == "SampleBinary 'sampleBinary'"
    }

    def "qualifies project scoped named and display name using owners name"() {
        def component = BaseComponentFixtures.createNode(MySampleComponent, MySampleComponent, new ModelRegistryHelper(), new DefaultComponentSpecIdentifier("path", "sample"))
        def binary = create(SampleBinary, MySampleBinary, "unitTest", component)

        expect:
        binary.name == "unitTest"
        binary.projectScopedName == "sampleUnitTest"
        binary.displayName == "SampleBinary 'sample:unitTest'"
    }

    def "create fails if subtype does not have a public no-args constructor"() {
        when:
        create(MyConstructedBinary, "sampleBinary")

        then:
        def e = thrown ModelInstantiationException
        e.message == "Could not create binary of type MyConstructedBinary"
        e.cause instanceof IllegalArgumentException
        e.cause.message.startsWith "Could not find any public constructor for class"
    }

    def "can own source sets"() {
        def binary = create(MySampleBinary, "sampleBinary")
        def customSourceSet = Stub(LanguageSourceSet) {
            getName() >> "custom"
        }
        def inputSourceSet = Stub(LanguageSourceSet) {
            getName() >> "input"
        }

        when:
        binary.sources.put("custom", customSourceSet)

        then:
        binary.sources.values()*.name == ["custom"]

        when:
        binary.inputs.add inputSourceSet

        then:
        binary.sources.values()*.name == ["custom"]
        binary.inputs*.name == ["input"]
    }

    def "source property is the same as inputs property"() {
        given:
        def binary = create(MySampleBinary, "sampleBinary")

        expect:
        binary.source == binary.inputs
    }

    private <T extends BaseBinarySpec> T create(Class<T> type, Class<T> implType = type, String name, MutableModelNode componentNode = null) {
        BaseBinaryFixtures.create(type, implType, name, componentNode, Mock(ITaskFactory))
    }
    
    static class MySampleComponent extends BaseComponentSpec {}

    interface SampleBinary extends BinarySpec {}

    static class MySampleBinary extends BaseBinarySpec implements SampleBinary {
    }
    static class MyConstructedBinary extends BaseBinarySpec {
        MyConstructedBinary(String arg) {}
    }

    static class CustomSourceSet extends BaseLanguageSourceSet {}
}
