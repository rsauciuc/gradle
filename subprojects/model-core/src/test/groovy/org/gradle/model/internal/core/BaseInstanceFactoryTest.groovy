/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core

import org.gradle.model.Managed
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Ignore
import spock.lang.Specification

class BaseInstanceFactoryTest extends Specification {
    static interface ThingSpec {}
    static interface ThingSpecInternal extends ThingSpec {}
    static abstract class BaseThingSpec implements ThingSpecInternal {}
    static class DefaultThingSpec extends BaseThingSpec {}
    static class DefaultOtherThingSpec extends BaseThingSpec implements OtherThingSpec {}
    static abstract class AbstractThingSpec implements ThingSpec {}

    static class NoDefaultConstructorThingSpec extends BaseThingSpec {
        @SuppressWarnings("GroovyUnusedDeclaration")
        NoDefaultConstructorThingSpec(String name) {
        }
    }
    static interface NotImplementedInternalViewSpec extends ThingSpec {}

    static interface OtherThingSpec extends ThingSpec {}
    static @Managed interface ManagedThingSpec extends ThingSpec {}
    static @Managed interface ChildManagedThingSpec extends ManagedThingSpec {}
    static @Managed interface ManagedThingSpecInternal {}
    static @Managed interface ManagedThingSpecInternalExtendingThingSpecInternal extends ThingSpecInternal {}
    static @Managed interface ManagedThingSpecInternalExtendingOtherThingSpec extends OtherThingSpec {}
    static interface UnmanagedThingSpec extends ThingSpec {}
    static @Managed interface BothThingSpec extends ThingSpec, OtherThingSpec {}

    def instanceFactory = new BaseInstanceFactory<ThingSpec>("things", ThingSpec, BaseThingSpec)
    def node = Mock(MutableModelNode)
    def factoryMock = Mock(InstanceFactory.ImplementationFactory)

    def "can register public type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))

        expect:
        instanceFactory.getSupportedTypes() == ([ModelType.of(ThingSpec)] as Set)
    }

    def "can register implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()
    }

    def "can register unmanaged internal view for unmanaged type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
            .withInternalView(ModelType.of(ThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()

        expect:
        instanceFactory.getInternalViews(ModelType.of(ThingSpec)) == ([ModelType.of(ThingSpecInternal)] as Set)
    }

    def "can register managed internal view for unmanaged type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
            .withInternalView(ModelType.of(ManagedThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()

        expect:
        instanceFactory.getInternalViews(ModelType.of(ThingSpec)) == ([ModelType.of(ManagedThingSpecInternal)] as Set)
    }

    def "internal views registered for super-type are returned"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("base"))
            .withInternalView(ModelType.of(ThingSpecInternal))
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed"))
            .withInternalView(ModelType.of(ManagedThingSpecInternal))
        instanceFactory.register(ModelType.of(ChildManagedThingSpec), new SimpleModelRuleDescriptor("child"))

        expect:
        instanceFactory.getInternalViews(ModelType.of(ThingSpec)) == ([
            ModelType.of(ThingSpecInternal),
        ] as Set)
        instanceFactory.getInternalViews(ModelType.of(ManagedThingSpec)) == ([
            ModelType.of(ThingSpecInternal),
            ModelType.of(ManagedThingSpecInternal)
        ] as Set)
        instanceFactory.getInternalViews(ModelType.of(ChildManagedThingSpec)) == ([
            ModelType.of(ThingSpecInternal),
            ModelType.of(ManagedThingSpecInternal)
        ] as Set)
    }

    def "can create instance"() {
        def thingMock = Mock(ThingSpec)
        def nodeMock = Mock(MutableModelNode)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        def instance = instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).create(nodeMock)

        then:
        instance == thingMock
        _ * nodeMock.path >> ModelPath.path("node.test")
        1 * factoryMock.create(ModelType.of(ThingSpec), "test", nodeMock) >> { thingMock }
        0 * _
    }

    def "fails when trying to create an unregistered type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.getImplementationInfo(ModelType.of(OtherThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Cannot create a '$OtherThingSpec.name' because this type is not known to things. Known types are: $ThingSpec.name"
    }

    def "fails when an implementation is registered that doesn't extend the base type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(Object), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$Object.name' registered for '$ThingSpec.name' must extend '$BaseThingSpec.name'"
    }

    def "fails when an implementation is registered that doesn't have a default constructor"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(NoDefaultConstructorThingSpec), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$NoDefaultConstructorThingSpec.name' registered for '$ThingSpec.name' must have a public default constructor"
    }

    def "fails when registered implementation type is an abstract type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(AbstractThingSpec), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$AbstractThingSpec.name' registered for '$ThingSpec.name' must not be abstract"
    }

    def "fails when implementation type is registered twice"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule 2"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot register implementation for type '$ThingSpec.name' because an implementation for this type was already registered by test rule"
    }

    def "fails when asking for implementation info for a non-managed type"() {
        when:
        instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(ThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Type '$ThingSpec.name' is not managed"
    }

    def "fails validation if default implementation does not implement internal view"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("view rule"))
            .withInternalView(ModelType.of(NotImplementedInternalViewSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ThingSpec.name' is invalid because the implementation type '$DefaultThingSpec.name' does not implement internal view '$NotImplementedInternalViewSpec.name'" +
            ", implementation type was registered by impl rule, internal view was registered by view rule"
    }

    def "fails validation if managed type extends interface without default implementation"() {
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ManagedThingSpec.name' is invalid because it doesn't extend an interface with a default implementation"
    }

    def "fails validation if unmanaged type extends interface with default implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(UnmanagedThingSpec), new SimpleModelRuleDescriptor("unmanaged thing"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$UnmanagedThingSpec.name' is invalid because no implementation was registered"
    }

    @Ignore("This would be hard to check, and we only use this internally, so we'll just be careful")
    def "fails validation if unmanaged type extends two interface with a default implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(OtherThingSpec), new SimpleModelRuleDescriptor("other impl rule"))
            .withImplementation(ModelType.of(DefaultOtherThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(BothThingSpec), new SimpleModelRuleDescriptor("both rule"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$BothThingSpec.name' is invalid because it has multiple default implementations registered, super-types that registered an implementation are: $ThingSpec.name, $OtherThingSpec.name"
    }

    def "fails when registering non-interface internal view"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withInternalView(ModelType.of(Object))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Internal view '$Object.name' registered for '$ThingSpec.name' must be an interface"
    }

    def "fails when registering unmanaged internal view for managed type"() {
        when:
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withInternalView(ModelType.of(ThingSpecInternal))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Internal view '$ThingSpecInternal.name' registered for managed type '$ManagedThingSpec.name' must be managed"
    }

    def "can register managed internal view for managed type that extends interface that is implemented by delegate type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))
            .withInternalView(ModelType.of(ManagedThingSpecInternalExtendingThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()
    }

    def "fails when registering managed internal view for managed type that extends interface that is not implemented by delegate type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))
            .withInternalView(ModelType.of(ManagedThingSpecInternalExtendingOtherThingSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ManagedThingSpec.name' is invalid because the default implementation type '$DefaultThingSpec.name' does not implement unmanaged internal view '$OtherThingSpec.name', internal view was registered by managed thing"
    }

}
