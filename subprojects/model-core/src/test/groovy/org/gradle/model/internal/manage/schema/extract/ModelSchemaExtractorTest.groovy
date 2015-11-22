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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.api.Action
import org.gradle.internal.reflect.MethodDescription
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.ModelSet
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.*
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TextUtil
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.regex.Pattern

import static org.gradle.model.internal.manage.schema.ModelProperty.StateManagementType.MANAGED
import static org.gradle.model.internal.manage.schema.ModelProperty.StateManagementType.UNMANAGED

@SuppressWarnings("GroovyPointlessBoolean")
class ModelSchemaExtractorTest extends Specification {
    @Shared
    def store = DefaultModelSchemaStore.getInstance()
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<? extends Serializable>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    static interface NotAnnotatedInterface {}

    def "unmanaged type"() {
        expect:
        extract(NotAnnotatedInterface) instanceof UnmanagedImplStructSchema
    }

    @Managed
    static class EmptyStaticClass {}

    def "must be interface"() {
        expect:
        fail EmptyStaticClass, "must be defined as an interface or an abstract class"
    }

    @Managed
    static interface ParameterizedEmptyInterface<T> {}

    def "cannot parameterize"() {
        expect:
        fail ParameterizedEmptyInterface, "cannot be a parameterized type"
    }

    @Managed
    static interface NoGettersOrSetters {
        void foo(String bar)
    }

    @Managed
    static interface HasExtraNonPropertyMethods {
        String getName()

        void setName(String name)

        void foo(String bar)
    }

    def "can only have getters and setters"() {
        expect:
        fail NoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(NoGettersOrSetters).takes(String)})")
        fail HasExtraNonPropertyMethods, Pattern.quote("nly paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(HasExtraNonPropertyMethods).takes(String)})")
    }

    @Managed
    static interface SingleStringNameProperty {
        String getName()

        void setName(String name)
    }

    def "extract single property"() {
        when:
        def properties = extract(SingleStringNameProperty).properties

        then:
        properties*.name == ["name"]
        properties*.type == [ModelType.of(String)]
    }

    @Managed
    static interface GetterWithParams {
        String getName(String name)

        void setName(String name)

    }

    def "malformed getter"() {
        expect:
        fail GetterWithParams, "getter methods cannot take parameters"
    }

    @Managed
    static interface NonVoidSetter {
        String getName()

        String setName(String name)
    }

    def "non void setter"() {
        expect:
        fail NonVoidSetter, "setter method must have void return type"
    }

    @Managed
    static interface SetterWithExtraParams {
        String getName()

        void setName(String name, String otherName)
    }

    def "setter with extra params"() {
        expect:
        fail SetterWithExtraParams, "setter method must have exactly one parameter"
    }

    @Managed
    static interface MisalignedSetterType {
        String getName()

        void setName(Object name)
    }

    def "misaligned setter type"() {
        expect:
        fail MisalignedSetterType, "setter method param must be of exactly the same type"
    }

    @Managed
    static interface SetterOnly {
        void setName(String name);
    }

    def "setter only"() {
        expect:
        fail SetterOnly, "only paired getter/setter methods are supported"
    }

    @Unroll
    def "primitive types are supported - #primitiveType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $primitiveType.name getPrimitiveProperty()

                void setPrimitiveProperty($primitiveType.name value)
            }
        """

        def properties = extract(interfaceWithPrimitiveProperty).properties

        then:
        properties*.name == ["primitiveProperty"]
        properties*.type == [ModelType.of(primitiveType)]

        where:
        primitiveType << [
            byte,
            boolean,
            char,
            float,
            long,
            short,
            int,
            double]
    }

    @Unroll
    def "Misaligned types #firstType and #secondType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $firstType.name getPrimitiveProperty()

                void setPrimitiveProperty($secondType.name value)
            }
        """

        then:
        fail(interfaceWithPrimitiveProperty, "(expected: ${firstType.name}, found: ${secondType.name})")

        where:
        firstType | secondType
        byte      | Byte
        boolean   | Boolean
        char      | Character
        float     | Float
        long      | Long
        short     | Short
        int       | Integer
        double    | Double
        Byte      | byte
        Boolean   | boolean
        Character | char
        Float     | float
        Long      | long
        Short     | short
        Integer   | int
        Double    | double
    }

    @Managed
    static interface MultipleProps {
        String getProp1();

        void setProp1(String string);

        String getProp2();

        void setProp2(String string);

        String getProp3();

        void setProp3(String string);
    }

    def "multiple properties"() {
        when:
        def properties = extract(MultipleProps).properties

        then:
        properties*.name == ["prop1", "prop2", "prop3"]
        properties*.type == [ModelType.of(String)] * 3
    }

    @Managed
    interface SelfReferencing {
        SelfReferencing getSelf()
    }

    def "can extract self referencing type"() {
        expect:
        extract(SelfReferencing).getProperty("self").type == ModelType.of(SelfReferencing)
    }

    @Managed
    interface HasSingleCharGetter {
        String getA()
        void setA(String a)
    }
    
    def "allow single char getters"() {
        when:
        def schema = store.getSchema(HasSingleCharGetter)
        
        then:
        schema instanceof ManagedImplSchema
        def a = schema.properties[0]
        assert a instanceof ModelProperty
        a.name == "a"
    }
    
    @Managed
    interface HasSingleCharFirstPartGetter {
        String getcCompiler()
        void setcCompiler(String cCompiler)
    }

    def "allows single char first camel-case part getters"() {
        when:
        def schema = store.getSchema(HasSingleCharFirstPartGetter)
        
        then:
        schema instanceof ManagedImplSchema
        def cCompiler = schema.properties[0]
        assert cCompiler instanceof ModelProperty
        cCompiler.name == "cCompiler"
    }
    
    @Managed
    interface HasDoubleCapsStartingGetter {
        String getCFlags()
        void setCFlags(String cflags)
    }

    def "allows double uppercase char first getters"() {
        when:
        def schema = store.getSchema(HasDoubleCapsStartingGetter)

        then:
        schema instanceof ManagedImplSchema
        def cflags = schema.properties[0]
        assert cflags instanceof ModelProperty
        // TODO:PM We violate the JavaBean spec here, property name should be CFlags
        cflags.name == "cFlags"
    }
    
    @Managed
    interface HasFullCapsGetter {
        String getURL()
        void setURL(String url)
    }

    def "allows full caps getters"() {
        when:
        def schema = store.getSchema(HasFullCapsGetter)

        then:
        schema instanceof ManagedImplSchema
        def url = schema.properties[0]
        assert url instanceof ModelProperty
        // TODO:PM We violate the JavaBean spec here, property name should be URL
        url.name == "uRL"
    }
    
    @Managed
    interface HasTwoFirstsCharLowercaseGetter {
        String getccCompiler()
        void setccCompiler()
    }
    
    def "reject two firsts char lowercase getters"() {
        expect:
        fail HasTwoFirstsCharLowercaseGetter, "the 4th character of the getter method name must be an uppercase character"
    }

    @Managed
    interface A1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface B1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface C1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface D1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    def "can extract incestuous nest"() {
        expect:
        extract(type).getProperty("a").type == extract(A1).type
        extract(type).getProperty("b").type == extract(B1).type
        extract(type).getProperty("c").type == extract(C1).type
        extract(type).getProperty("d").type == extract(D1).type

        where:
        type << [A1, B1, C1, D1]
    }

    @Managed
    static interface WithInheritedProperties extends SingleStringNameProperty {
        Integer getCount()

        void setCount(Integer count)
    }

    def "extracts inherited properties"() {
        when:
        def properties = extract(WithInheritedProperties).properties

        then:
        properties*.name == ["count", "name"]
    }

    @Managed
    static interface SingleIntegerValueProperty {
        Integer getValue()

        void setValue(Integer count)
    }

    @Managed
    static interface WithMultipleParents extends SingleStringNameProperty, SingleIntegerValueProperty {
    }

    def "extracts properties from multiple parents"() {
        when:
        def properties = extract(WithMultipleParents).properties

        then:
        properties*.name == ["name", "value"]
    }

    static interface SinglePropertyNotAnnotated {
        String getName()

        void setName(String name)
    }

    @Managed
    static interface WithInheritedPropertiesFromGrandparent extends WithInheritedProperties {
        Boolean getFlag()

        void setFlag(Boolean flag)
    }

    def "extracts properties from multiple levels of inheritance"() {
        when:
        def properties = extract(WithInheritedPropertiesFromGrandparent).properties

        then:
        properties*.name == ["count", "flag", "name"]
    }

    static interface WithInheritedPropertiesFromNotAnnotated extends SinglePropertyNotAnnotated {
        Integer getCount()

        void setCount(Integer count)
    }

    def "can extract inherited properties from an interface not annotated with @Managed"() {
        when:
        def properties = extract(WithInheritedPropertiesFromNotAnnotated).properties

        then:
        properties*.name == ["count", "name"]
    }

    @Managed
    static interface SingleStringValueProperty {
        String getValue()

        void setValue(String value)
    }

    @Managed
    static interface SingleFloatValueProperty {
        Float getValue()

        void setValue(Float value)
    }

    @Managed
    static interface ConflictingPropertiesInParents extends SingleIntegerValueProperty, SingleStringValueProperty, SingleFloatValueProperty {
    }

    def "conflicting properties of super types are detected"() {
        given:
        def invalidMethods = [
            MethodDescription.name("getValue").owner(SingleFloatValueProperty).returns(Float).takes(),
            MethodDescription.name("getValue").owner(SingleIntegerValueProperty).returns(Integer).takes(),
            MethodDescription.name("getValue").owner(SingleStringValueProperty).returns(String).takes(),
        ]
        def message = Pattern.quote("overloaded methods are not supported (invalid methods: ${invalidMethods.join(", ")})")

        expect:
        fail ConflictingPropertiesInParents, message
    }

    @Managed
    static interface AnotherSingleStringValueProperty {
        String getValue()

        void setValue(String value)
    }

    @Managed
    static interface SamePropertyInMultipleTypes extends SingleStringValueProperty, AnotherSingleStringValueProperty {
    }

    def "exact same properties defined in multiple types of the hierarchy are allowed"() {
        when:
        def properties = extract(SamePropertyInMultipleTypes).properties

        then:
        properties*.name == ["value"]
    }

    @Managed
    static interface ReadOnlyProperty {
        SingleStringValueProperty getSingleStringValueProperty()
    }

    @Managed
    static interface WritableProperty extends ReadOnlyProperty {
        void setSingleStringValueProperty(SingleStringValueProperty value)
    }

    def "read only property of a super type can be made writable"() {
        when:
        def properties = extract(WritableProperty).properties

        then:
        properties*.writable == [true]
    }

    @Managed
    static interface ChildWithNoGettersOrSetters extends NoGettersOrSetters {
    }

    def "invalid methods of super types are reported"() {
        expect:
        fail ChildWithNoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(NoGettersOrSetters).takes(String)})")
    }

    def "type argument of a managed set has to be specified"() {
        given:
        def type = ModelType.returnType(TypeHolder.getDeclaredMethod("noParam"))

        expect:
        fail type, "type parameter of $ModelSet.name has to be specified"
    }

    static interface TypeHolder {
        ModelSet noParam();
    }

    @Managed
    interface Thing {}

    @Managed
    interface SpecialThing extends Thing {}

    @Managed
    interface SimpleModel {
        Thing getThing()
    }

    @Managed
    interface SpecialModel extends SimpleModel {
        SpecialThing getThing()

        void setThing(SpecialThing thing)
    }

    def "a subclass may specialize a property type"() {
        when:
        def schema = extract(SpecialModel)

        then:
        schema.properties*.type == [ModelType.of(SpecialThing)]
    }

    @Unroll
    def "type argument of a managed set cannot be a wildcard - #type"() {
        expect:
        fail type, "type parameter of $ModelSet.name cannot be a wildcard"

        where:
        type << [
            new ModelType<ModelSet<?>>() {},
            new ModelType<ModelSet<? extends A1>>() {},
            new ModelType<ModelSet<? super A1>>() {}
        ]
    }

    //TODO - AK
    @Ignore
    def "type argument of a managed set has to be managed"() {
        given:
        def type = new ModelType<ModelSet<Object>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == "Invalid managed model type ${new ModelType<ModelSet<Object>>() {}}: cannot create a managed set of type $Object.name as it is an unmanaged type. Only @Managed types are allowed."
    }

    def "type argument of a managed set has to be a valid managed type"() {
        given:
        def type = new ModelType<ModelSet<SetterOnly>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        def invalidMethodDescription = MethodDescription.name("setName").returns(void.class).owner(SetterOnly).takes(String)
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $SetterOnly.name: only paired getter/setter methods are supported (invalid methods: ${invalidMethodDescription}).
The type was analyzed due to the following dependencies:
$type
  \\--- element type ($SetterOnly.name)""")
    }

    def "specializations of managed set are not supported"() {
        given:
        def type = new ModelType<SpecialModelSet<A1>>() {}

        expect:
        fail type, "subtyping $ModelSet.name is not supported"
    }

    def "managed sets of managed set are not supported"() {
        given:
        def type = new ModelType<ModelSet<ModelSet<A1>>>() {}

        expect:
        fail type, "$ModelSet.name cannot be used as type parameter of $ModelSet.name"
    }

    static class MyBigInteger extends BigInteger {
        MyBigInteger(String s) {
            super(s)
        }
    }

    static class MyBigDecimal extends BigDecimal {
        MyBigDecimal(String s) {
            super(s)
        }
    }

    def "cannot subclass non final value type"() {
        expect:
        fail MyBigInteger, Pattern.quote("subclasses of java.math.BigInteger are not supported")
        fail MyBigDecimal, Pattern.quote("subclasses of java.math.BigDecimal are not supported")
    }

    static enum MyEnum {
        A, B, C
    }

    def "can extract enum"() {
        expect:
        extract(MyEnum) instanceof ScalarValueSchema
    }

    @Managed
    static interface HasUnmanagedOnManaged {
        @Unmanaged
        MyEnum getMyEnum();

        void setMyEnum(MyEnum myEnum)
    }

    def "cannot annotate managed type property with unmanaged"() {
        expect:
        fail HasUnmanagedOnManaged, Pattern.quote("property 'myEnum' is marked as @Unmanaged, but is of @Managed type")
    }

    private void fail(extractType, String msgPattern) {
        fail(extractType, extractType, msgPattern)
    }

    def "subtype can declare property unmanaged"() {
        expect:
        extract(ExtendsMissingUnmanaged).getProperty("thing").type.rawClass == InputStream
    }

    @Managed
    static interface ExtendsMissingUnmanaged {
        @Unmanaged
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    @Managed
    static interface NoSetterForUnmanaged {
        @Unmanaged
        InputStream getThing();
    }

    def "must have setter for unmanaged"() {
        expect:
        fail NoSetterForUnmanaged, Pattern.quote("unmanaged property 'thing' cannot be read only, unmanaged properties must have setters")
    }

    @Managed
    static interface AddsSetterToNoSetterForUnmanaged extends NoSetterForUnmanaged {
        void setThing(InputStream inputStream);
    }

    def "subtype can add unmanaged setter"() {
        expect:
        extract(AddsSetterToNoSetterForUnmanaged).getProperty("thing").type.rawClass == InputStream
    }

    @Managed
    static abstract class NonAbstractGetterWithSetter {
        String getName() {}

        abstract void setName(String name)
    }

    @Managed
    static abstract class NonAbstractSetter {
        abstract String getName()

        void setName(String name) {}
    }

    def "non-abstract mutator methods are not allowed"() {
        expect:
        fail NonAbstractGetterWithSetter, Pattern.quote("setters are not allowed for non-abstract getters (invalid method: ${MethodDescription.name("setName").owner(NonAbstractGetterWithSetter).returns(void.class).takes(String)})")
        fail NonAbstractSetter, Pattern.quote("non-abstract setters are not allowed (invalid method: ${MethodDescription.name("setName").owner(NonAbstractSetter).takes(String).returns(void.class)})")
    }

    @Managed
    static abstract class ConstructorWithArguments {
        ConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class AdditionalConstructorWithArguments {
        AdditionalConstructorWithArguments() {}

        AdditionalConstructorWithArguments(String arg) {}
    }

    static class SuperConstructorWithArguments {
        SuperConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class ConstructorCallingSuperConstructorWithArgs extends SuperConstructorWithArguments {
        ConstructorCallingSuperConstructorWithArgs() {
            super("foo")
        }
    }

    @Managed
    static abstract class CustomConstructorInSuperClass extends ConstructorCallingSuperConstructorWithArgs {
    }

    def "custom constructors are not allowed"() {
        expect:
        fail ConstructorWithArguments, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(ConstructorWithArguments).takes(String)})")
        fail AdditionalConstructorWithArguments, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(AdditionalConstructorWithArguments).takes(String)})")
        fail CustomConstructorInSuperClass, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(SuperConstructorWithArguments).takes(String)})")
    }

    @Managed
    static abstract class WithInstanceScopedField {
        private String name
        private int age
    }

    @Managed
    static abstract class WithInstanceScopedFieldInSuperclass extends WithInstanceScopedField {
    }

    def "instance scoped fields are not allowed"() {
        expect:
        fail WithInstanceScopedField, Pattern.quote("instance scoped fields are not allowed (found fields: private int ${WithInstanceScopedField.name}.age, private java.lang.String ${WithInstanceScopedField.name}.name)")
        fail WithInstanceScopedFieldInSuperclass, Pattern.quote("instance scoped fields are not allowed (found fields: private int ${WithInstanceScopedField.name}.age, private java.lang.String ${WithInstanceScopedField.name}.name)")
    }


    @Managed
    static abstract class ProtectedAbstractMethods {
        protected abstract String getName()

        protected abstract void setName(String name)
    }

    @Managed
    static abstract class ProtectedAbstractMethodsInSuper extends ProtectedAbstractMethods {
    }

    def "protected abstract methods are not allowed"() {
        given:
        def getterDescription = MethodDescription.name("getName").owner(ProtectedAbstractMethods).takes().returns(String)
        def setterDescription = MethodDescription.name("setName").owner(ProtectedAbstractMethods).returns(void.class).takes(String)

        expect:
        fail ProtectedAbstractMethods, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
        fail ProtectedAbstractMethodsInSuper, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethods {
        protected String getName() {
            return null;
        }

        private void setName(String name) {}
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethodsInSuper extends ProtectedAndPrivateNonAbstractMethods {
    }

    def "protected and private non-abstract methods are not allowed"() {
        given:
        def getterDescription = MethodDescription.name("getName").owner(ProtectedAndPrivateNonAbstractMethods).takes().returns(String)
        def setterDescription = MethodDescription.name("setName").owner(ProtectedAndPrivateNonAbstractMethods).returns(void.class).takes(String)

        expect:
        fail ProtectedAndPrivateNonAbstractMethods, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
        fail ProtectedAndPrivateNonAbstractMethodsInSuper, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
    }

    interface SomeMap extends ModelMap<List<String>> {
    }

    def "specialized map"() {
        expect:
        def schema = extract(SomeMap)
        assert schema instanceof SpecializedMapSchema
        schema.elementType == new ModelType<List<String>>() {}
        schema.implementationType
    }

    private void fail(extractType, errorType, String msgPattern) {
        try {
            extract(extractType)
            throw new AssertionError("schema extraction from ${getName(extractType)} should failed with message: $msgPattern")
        } catch (InvalidManagedModelElementTypeException e) {
            assert e.message.startsWith("Invalid managed model type ${getName(errorType)}: ")
            assert e.message =~ msgPattern
        }
    }

    private ModelSchema<?> extract(ModelType<?> modelType) {
        store.getSchema(modelType)
    }

    private ModelSchema<?> extract(Class<?> clazz) {
        extract(ModelType.of(clazz))
    }

    @Unroll
    def "can extract a simple managed type with a property of #type"() {
        when:
        Class<?> generatedClass = managedClass(type)

        then:
        extract(generatedClass)

        where:
        type << JDK_SCALAR_TYPES
    }

    Class<?> managedClass(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
    void set${typeName}(${typeName} a${typeName})
}
""")
    }

    static class CustomThing {}

    static class UnmanagedThing {}

    def "can register custom strategy"() {
        when:
        def strategy = Mock(ModelSchemaExtractionStrategy) {
            extract(_) >> { ModelSchemaExtractionContext extractionContext ->
                if (extractionContext.type.rawClass == CustomThing) {
                    extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
                }
            }
        }
        def extractor = new ModelSchemaExtractor([strategy], new ModelSchemaAspectExtractor())
        def store = new DefaultModelSchemaStore(extractor)

        then:
        store.getSchema(CustomThing) instanceof ScalarValueSchema
        store.getSchema(UnmanagedThing) instanceof UnmanagedImplStructSchema
    }

    def "custom strategy can register dependency on other type"() {
        def strategy = Mock(ModelSchemaExtractionStrategy)
        def extractor = new ModelSchemaExtractor([strategy], new ModelSchemaAspectExtractor())
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def customSchema = store.getSchema(CustomThing)

        then:
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(CustomThing)
            extractionContext.child(ModelType.of(UnmanagedThing), "child")
            extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
        }
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(UnmanagedThing)
        }

        and:
        customSchema instanceof ScalarValueSchema
    }

    def "validator is invoked after all dependencies have been visited"() {
        def strategy = Mock(ModelSchemaExtractionStrategy)
        def validator = Mock(Action)
        def extractor = new ModelSchemaExtractor([strategy], new ModelSchemaAspectExtractor())
        def store = new DefaultModelSchemaStore(extractor)

        when:
        store.getSchema(CustomThing)

        then:
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(CustomThing)
            extractionContext.addValidator(validator)
            extractionContext.child(ModelType.of(UnmanagedThing), "child")
            extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
        }
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(UnmanagedThing)
            return null;
        }

        then:
        1 * validator.execute(_)
    }

    def "model map type doesn't have to be managed type in an unmanaged type"() {
        expect:
        extract(UnmanagedModelMapInUnmanagedType).getProperty("things").type.rawClass == ModelMap
    }

    static abstract class UnmanagedModelMapInUnmanagedType {
        ModelMap<InputStream> getThings() { null }
    }

    static class SimpleUnmanagedType {
        String prop

        String getCalculatedProp() {
            "calc"
        }
    }

    def "can retrieve property value"() {
        def instance = new SimpleUnmanagedType(prop: "12")

        when:
        def schema = extract(SimpleUnmanagedType)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.getProperty("prop").getPropertyValue(instance) == "12"
        schema.getProperty("calculatedProp").getPropertyValue(instance) == "calc"
    }

    static abstract class SimpleUnmanagedTypeWithAnnotations {
        @CustomTestAnnotation("unmanaged")
        abstract String getUnmanagedProp()

        @CustomTestAnnotation("unmanagedSetter")
        abstract void setUnmanagedProp(String value)

        @CustomTestAnnotation("unmanagedCalculated")
        String getUnmanagedCalculatedProp() {
            return "unmanaged-calculated"
        }

        boolean isBuildable() { true }

        int getTime() { 0 }
    }

    def "properties are extracted from unmanaged type"() {
        when:
        def schema = extract(SimpleUnmanagedTypeWithAnnotations)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.properties*.name == ["buildable", "time", "unmanagedCalculatedProp", "unmanagedProp"]

        schema.getProperty("unmanagedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedProp").isWritable() == true
        schema.getProperty("unmanagedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false
        schema.getProperty("buildable").stateManagementType == UNMANAGED
        schema.getProperty("buildable").isWritable() == false
        schema.getProperty("time").stateManagementType == UNMANAGED
        schema.getProperty("time").isWritable() == false
    }

    static interface UnmanagedSuperType {
        @CustomTestAnnotation("unmanaged")
        abstract String getUnmanagedProp()

        @CustomTestAnnotation("unmanagedSetter")
        abstract void setUnmanagedProp(String value)

        @CustomTestAnnotation("unmanagedCalculated")
        String getUnmanagedCalculatedProp()

        boolean isBuildable()

        int getTime()
    }

    @Managed
    static abstract class ManagedTypeWithAnnotationsExtendingUnmanagedType implements UnmanagedSuperType {
        @CustomTestAnnotation("managed")
        abstract String getManagedProp()

        @CustomTestAnnotation("managedSetter")
        abstract void setManagedProp(String managedProp)

        @CustomTestAnnotation("managedCalculated")
        String getManagedCalculatedProp() {
            return "calc"
        }
    }

    def "properties are extracted from unmanaged type with managed super-type"() {
        def extractor = new ModelSchemaExtractor([
            new TestUnmanagedTypeWithManagedSuperTypeExtractionStrategy(UnmanagedSuperType)
        ], new ModelSchemaAspectExtractor())
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def schema = store.getSchema(ManagedTypeWithAnnotationsExtendingUnmanagedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["buildable", "managedCalculatedProp", "managedProp", "time", "unmanagedCalculatedProp", "unmanagedProp"]

        schema.getProperty("unmanagedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedProp").isWritable() == true

        schema.getProperty("unmanagedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false

        schema.getProperty("buildable").stateManagementType == UNMANAGED
        schema.getProperty("buildable").isWritable() == false

        schema.getProperty("time").stateManagementType == UNMANAGED
        schema.getProperty("time").isWritable() == false
    }

    @Managed
    static abstract class SimplePurelyManagedType {
        @CustomTestAnnotation("managed")
        abstract String getManagedProp()

        @CustomTestAnnotation("managedSetter")
        abstract void setManagedProp(String managedProp)

        @CustomTestAnnotation("managedCalculated")
        String getManagedCalculatedProp() {
            return "calc"
        }
    }

    def "properties are extracted from managed type"() {
        when:
        def schema = extract(SimplePurelyManagedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["managedCalculatedProp", "managedProp"]

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false
    }

    @Managed
    static abstract class OverridingManagedSubtype extends SimplePurelyManagedType {
        @Override
        @CustomTestAnnotation("overriddenManaged")
        @CustomTestAnnotation2
        abstract String getManagedProp()

        @Override
        @CustomTestAnnotation2
        String getManagedCalculatedProp() { return "overridden " }
    }

    def "property annotations when overridden retain most significant value"() {
        when:
        def schema = extract(OverridingManagedSubtype)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["managedCalculatedProp", "managedProp"]

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false
    }

    class MyAspect implements ModelSchemaAspect {}

    @Managed
    static abstract class MyTypeOfAspect {
        abstract String getProp()

        abstract void setProp(String prop)

        String getCalculatedProp() {
            return "calc"
        }
    }

    def "aspects can be extracted"() {
        def aspect = new MyAspect()
        def aspectExtractionStrategy = Mock(ModelSchemaAspectExtractionStrategy)
        def extractor = new ModelSchemaExtractor([], new ModelSchemaAspectExtractor([aspectExtractionStrategy]))
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def resultSchema = store.getSchema(MyTypeOfAspect)

        then:
        assert resultSchema instanceof StructSchema
        resultSchema.hasAspect(MyAspect)
        resultSchema.getAspect(MyAspect) == aspect

        1 * aspectExtractionStrategy.extract(_, _) >> { ModelSchemaExtractionContext<?> extractionContext, List<ModelPropertyExtractionResult> propertyResults ->
            assert propertyResults*.property*.name == ["calculatedProp", "prop"]
            return new ModelSchemaAspectExtractionResult(aspect)
        }
        0 * _
    }

    @Managed
    interface HasIsTypeGetter {
        boolean isRedundant()

        void setRedundant(boolean redundant)
    }

    @Managed
    interface HasGetTypeGetter {
        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "supports a boolean property with a get style getter"() {
        when:
        store.getSchema(ModelType.of(HasGetTypeGetter))

        then:
        noExceptionThrown()
    }

    @Managed
    interface HasDualGetter {
        boolean isRedundant()

        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "allows both is and get style getters"() {
        when:
        def schema = store.getSchema(HasDualGetter)

        then:
        schema instanceof ManagedImplSchema
        def redundant = schema.properties[0]
        assert redundant instanceof ModelProperty
        redundant.getters.size() == 2
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBoolean {
        String isThing()

        void setThing(String thing)
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean {
        Boolean isThing()

        void setThing(Boolean thing)
    }

    def "supports a boolean property with an is style getter"() {
        expect:
        store.getSchema(ModelType.of(HasIsTypeGetter))
    }

    @Unroll
    def "should not allow 'is' as a prefix for getter on non primitive boolean"() {
        when:
        store.getSchema(IsNotAllowedForOtherTypeThanBoolean)

        then:
        def ex = thrown(InvalidManagedModelElementTypeException)
        ex.message =~ /getter method name must start with 'get'/

        where:
        managedType << [IsNotAllowedForOtherTypeThanBoolean, IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean]
    }

    abstract class HasStaticProperties {
        static String staticValue
        String value
    }

    def "does not extract static properties"() {
        def schema = store.getSchema(HasStaticProperties)
        expect:
        schema.properties*.name == ["value"]
    }

    abstract class HasProtectedAndPrivateProperties {
        String value
        protected String protectedValue
        private String privateValue
    }

    def "does not extract protected and private properties"() {
        def schema = store.getSchema(HasProtectedAndPrivateProperties)
        expect:
        schema.properties*.name == ["value"]
    }

    @Managed
    interface HasIsAndGetPropertyWithDifferentTypes {
        boolean isValue()

        String getValue()
    }

    def "handles is/get property with non-matching type"() {
        when:
        store.getSchema(HasIsAndGetPropertyWithDifferentTypes)

        then:
        def ex = thrown InvalidManagedModelElementTypeException
        ex.message.contains "property 'value' has both 'isValue()' and 'getValue()' getters, but they don't both return a boolean"
    }

    @Unroll
    def "supports read-only List<#type.simpleName> property"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<${type.simpleName}> getItems()
            }
        """

        def schema = extract(managedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == MANAGED
        schema.getProperty("items").isWritable() == false

        where:
        type << JDK_SCALAR_TYPES
    }

    @Unroll
    def "read-write List<#type.simpleName> property is allowed"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<${type.simpleName}> getItems()
                void setItems(List<${type.simpleName}> items)
            }
        """

        def schema = extract(managedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == MANAGED
        schema.getProperty("items").isWritable() == true

        where:
        type << JDK_SCALAR_TYPES
    }

    def "displays a reasonable error message when getter and setter of a property of collection of scalar types do not use the same generic type"() {
        given:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<String> getItems()
                void setItems(List<Integer> integers)
            }
        """

        when:
        extract(managedType)

        then:
        InvalidManagedModelElementTypeException ex = thrown()
        ex.message.contains 'setter method param must be of exactly the same type as the getter returns (expected: java.util.List<java.lang.String>, found: java.util.List<java.lang.Integer>)'
    }

    @Unroll
    def "should not throw an error if we use unsupported collection type #collectionType.simpleName on a non-managed type"() {
        given:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            interface CollectionType {
                ${collectionType.name}<String> getItems()
            }
        """

        when:
        def schema = extract(managedType)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == UNMANAGED
        schema.getProperty("items").isWritable() == false

        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }


    def "can extract overloaded property from unmanaged struct type"() {
        def unmanagedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            interface UnmanagedWithOverload {
                Integer getThing()
                Integer getThing(int parameter)

                boolean isNice()
                boolean getNice()
                boolean getNice(int parameter)
                boolean isNice(int parameter)
                void setNice(boolean parameter)
            }
        """

        when:
        def schema = extract(unmanagedType)
        assert schema instanceof StructSchema

        then:
        schema.properties*.name == ["nice", "thing"]
        schema.properties*.type*.rawClass == [boolean, Integer]
    }
    
    interface UnmanagedSuperTypeWithMethod {
        InputStream doSomething(Object param)
    }
    
    @Managed
    interface ManagedTypeWithOverriddenMethodExtendingUnmanagedTypeWithMethod extends UnmanagedSuperTypeWithMethod {
        @Override InputStream doSomething(Object param)
    }
    
    def "accept methods from unmanaged supertype overridden in managed type"() {
        expect:
        extract(ManagedTypeWithOverriddenMethodExtendingUnmanagedTypeWithMethod) instanceof ManagedImplStructSchema
    }

    @Managed
    interface ManagedTypeWithCovarianceOverriddenMethodExtendingUnamangedTypeWithMethod extends UnmanagedSuperTypeWithMethod {
        @Override ByteArrayInputStream doSomething(Object param)
    }
    
    def "accept methods from unmanaged supertype with covariance overridden in managed type"() {
        expect:
        extract(ManagedTypeWithCovarianceOverriddenMethodExtendingUnamangedTypeWithMethod) instanceof ManagedImplStructSchema
    }

    interface UnmanagedSuperTypeWithOverloadedMethod {
        InputStream doSomething(Object param)
        InputStream doSomething(Object param, Object other)
    }
    
    @Managed
    interface ManagedTypeExtendingUnmanagedTypeWithOverloadedMethod extends UnmanagedSuperTypeWithOverloadedMethod {
        @Override ByteArrayInputStream doSomething(Object param)
        @Override ByteArrayInputStream doSomething(Object param, Object other)
    }
    
    def "accept overloaded overridden methods from unmanaged supertype overridden in managed type"() {
        expect:
        extract(ManagedTypeExtendingUnmanagedTypeWithOverloadedMethod) instanceof ManagedImplStructSchema
    }

    String getName(ModelType<?> modelType) {
        modelType
    }

    String getName(Class<?> clazz) {
        clazz.name
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface CustomTestAnnotation {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface CustomTestAnnotation2 {}
