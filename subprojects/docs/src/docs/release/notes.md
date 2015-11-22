## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Performance improvements

Should mention Java compile avoidance

### Software model changes

TBD - Binary names are now scoped to the component they belong to. This means multiple components can have binaries with a given name. For example, several library components
might have a `jar` binary. This allows binaries to have names that reflect their relationship to the component, rather than their absolute location in the software model.

#### Component level dependencies for Java libraries

In most cases it is more natural and convenient to define dependencies per component rather than individually on a source set and it is now possible to do so when defining a Java library.

Example:

    apply plugin: "jvm-component"

    model {
      components {
        main(JvmLibrarySpec) {
          dependencies {
            library "core"
          }
        }

        core(JvmLibrarySpec) {
        }
      }
    }

Dependencies declared this way will apply to all source sets for the component.

#### Managed internal views for binaries and components

Now it is possible to attach a `@Managed` internal view to any `BinarySpec` or `ComponentSpec` type. This allows pluign authors to attach extra properties to already registered binary and component types like `JarBinarySpec`.

Example:

    @Managed
    interface MyJarBinarySpecInternal extends JarBinarySpec {
        String getInternal()
        void setInternal(String internal)
    }

    class CustomPlugin extends RuleSource {
        @BinaryType
        public void register(BinaryTypeBuilder<JarBinarySpec> builder) {
            builder.internalView(MyJarBinarySpecInternal)
        }

        @Mutate
        void mutateInternal(ModelMap<MyJarBinarySpecInternal> binaries) {
            // ...
        }
    }

    apply plugin: "jvm-component"

    model {
        components {
            myComponent(JvmLibrarySpec) {
                binaries.withType(MyJarBinarySpecInternal) { binary ->
                    binary.internal = "..."
                }
            }
        }
    }

Note: `@Managed` internal views registered on unmanaged types (like `JarBinarySpec`) are not yet visible in the top-level `binaries` container, and thus it's impossible to do things like:

    // This won't work:
    model {
        binaries.withType(MyJarBinarySpecInternal) {
            // ...
        }
    }

This feature is available for subtypes of `BinarySpec` and `ComponentSpec`.

#### Managed binary and component types

The `BinarySpec` and `ComponentSpec` types can now be extended via `@Managed` subtypes, allowing for declaration of `@Managed` components and binaries without having to provide a default implementation. `LibrarySpec` and `ApplicationSpec` can also be extended in this manner.

Example:

    @Managed
    interface SampleLibrarySpec extends LibrarySpec {
        String getPublicData()
        void setPublicData(String publicData)
    }

    class RegisterComponentRules extends RuleSource {
        @ComponentType
        void register(ComponentTypeBuilder<SampleLibrarySpec> builder) {
        }
    }
    apply plugin: RegisterComponentRules

    model {
        components {
            sampleLib(SampleLibrarySpec) {
                publicData = "public"
            }
        }
    }


#### Default implementation for unmanaged base binary and component types

It is now possible to declare a default implementation for a base component or a binary type, and extend it via further managed subtypes.

    interface MyBaseBinarySpec extends BinarySpec {}

    class MyBaseBinarySpecImpl extends BaseBinarySpec implements MyBaseBinarySpec {}

    class BasePlugin extends RuleSource {
        @ComponentType
        public void registerMyBaseBinarySpec(ComponentTypeBuilder<MyBaseBinarySpec> builder) {
            builder.defaultImplementation(MyBaseBinarySpecImpl.class);
        }
    }

    @Managed
    interface MyCustomBinarySpec extends BaseBinarySpec {
        // Add some further managed properties
    }

    class CustomPlugin extends RuleSource {
        @ComponentType
        public void registerMyCustomBinarySpec(ComponentTypeBuilder<MyCustomBinarySpec> builder) {
            // No default implementation required
        }
    }

This functionality is available for unmanaged types extending `ComponentSpec` and `BinarySpec`.

#### Internal views for unmanaged binary and component types

The goal of the new internal views feature is for plugin authors to be able to draw a clear line between public and internal APIs of their plugins regarding model elements.
By declaring some functionality in internal views (as opposed to exposing it on a public type), the plugin author can let users know that the given functionality is intended
for the plugin's internal bookkeeping, and should not be considered part of the public API of the plugin.

Internal views must be interfaces, but they don't need to extend the public type they are registered for.

**Example:** A plugin could introduce a new binary type like this:

    /**
     * Documented public type exposed by the plugin
     */
    interface MyBinarySpec extends BinarySpec {
        // Functionality exposed to the public
    }

    // Undocumented internal type used by the plugin itself only
    interface MyBinarySpecInternal extends MyBinarySpec {
        String getInternalData();
        void setInternalData(String internalData);
    }

    class MyBinarySpecImpl implements MyBinarySpecInternal {
        private String internalData;
        String getInternalData() { return internalData; }
        void setInternalData(String internalData) { this.internalData = internalData; }
    }

    class MyBinarySpecPlugin extends RuleSource {
        @BinaryType
        public void registerMyBinarySpec(BinaryTypeBuilder<MyBinarySpec> builder) {
            builder.defaultImplementation(MyBinarySpecImpl.class);
            builder.internalView(MyBinarySpecInternal.class);
        }
    }

With this setup the plugin can expose `MyBinarySpec` to the user as the public API, while it can attach some additional information to each of those binaries internally.

Internal views registered for an unmanaged public type must be unmanaged themselves, and the default implementation of the public type must implement the internal view
(as `MyBinarySpecImpl` implements `MyBinarySpecInternal` in the example above).

It is also possible to attach internal views to `@Managed` types as well:

    @Managed
    interface MyManagedBinarySpec extends MyBinarySpec {}

    @Managed
    interface MyManagedBinarySpecInternal extends MyManagedBinarySpec {}

    class MyManagedBinarySpecPlugin extends RuleSource {
        @BinaryType
        public void registerMyManagedBinarySpec(BinaryTypeBuilder<MyManagedBinarySpec> builder) {
            builder.internalView(MyManagedBinarySpecInternal.class);
        }
    }

Internal views registered for a `@Managed` public type must themselves be `@Managed`.

This functionality is available for types extending `ComponentSpec` and `BinarySpec`.

### TestKit dependency decoupled from Gradle core dependencies

The method `DependencyHandler.gradleTestKit()` creates a dependency on the classes of the Gradle TestKit runtime classpath. In previous versions
of Gradle the TestKit dependency also declared transitive dependencies on other Gradle core classes and external libraries that ship with the Gradle distribution. This might lead to
version conflicts between the runtime classpath of the TestKit and user-defined libraries required for functional testing. A typical example for this scenario would be Google Guava.
With this version of Gradle, the Gradle TestKit dependency is represented by a fat and shaded JAR file containing Gradle core classes and classes of all required external dependencies
to avoid polluting the functional test runtime classpath.

### Visualising a project's build script dependencies

The new `buildEnvironment` task can be used to visualise the project's `buildscript` dependencies.
This task is implicitly available for all projects, much like the existing `dependencies` task.

The `buildEnvironment` task can be used to understand how the declared dependencies of project's build script actually resolve,
including transitive dependencies.

The feature was kindly contributed by [Ethan Hall](https://github.com/ethankhall).

### Checkstyle HTML report

The [`Checkstyle` task](dsl/org.gradle.api.plugins.quality.Checkstyle.html) now produces a HTML report on failure in addition to the existing XML report.
The, more human friendly, HTML report is now advertised instead of the XML report when it is available.

This feature was kindly contributed by [Sebastian Schuberth](https://github.com/sschuberth).

### Model rules improvements

#### Support for `LanguageSourceSet` model elements

This release facilitates adding source sets (subtypes of `LanguageSourceSet`) to arbitrary locations in the model space. A `LanguageSourceSet` can be attached to any @Managed type as a property, or used for
the elements of a ModelSet or ModelMap, or as a top level model element in it's own right.

### Support for external dependencies in the 'jvm-components' plugin

It is now possible to reference external dependencies when building a `JvmLibrary` using the `jvm-component` plugin.

TODO: Expand this and provide a DSL example.

### Model DSL improvements

TODO: `ModelMap` creation and configuration DSL syntax is now treated as nested rule. For example, an element can be configured using the configuration of a sibling as input:

    model {
        components {
            mylib { ... }
            test {
                targetPlatform = $.components.mylib.targetPlatform
            }
        }
    }

This means that a task can be configured using another task as input:

    model {
        tasks {
            jar { ... }
            dist(Zip) {
                def jar = $.tasks.jar // The `jar` task has been fully configured and will not change any further
                from jar.output
                into someDir
            }
        }
    }

This is also available for the various methods of `ModelMap`, such as `all` or `withType`:

    model {
        components {
            all {
                // Adds a rule for each component
                ...
            }
            withType(JvmLibrarySpec) {
                // Adds a rule for each JvmLibrarySpec component
                ...
            }
        }
    }

TODO: The properties of a `@Managed` type can be configured using nested configure methods:

    model {
        components {
            mylib {
                sources {
                    // Adds a rule to configure `mylib.sources`
                    ..
                }
                binaries {
                    // Adds a rule to configure `mylib.sources`
                    ...
                }
            }
        }
    }

This is automatically added for any property whose type is `@Managed`, or a `ModelMap<T>` or `ModelSet<T>`.

### Tooling API exposes source language level on EclipseProject model

The `EclipseProject` model now exposes the Java source language level via the
<a href="javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getJavaSourceSettings">`getJavaSourceSettings()`</a> method.
IDE providers use this method to automatically determine the source language level. In turn users won't have to configure that anymore via the Gradle Eclipse plugin.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to TestKit's runtime classpath

- External dependencies e.g. Google Guava brought in by Gradle core libraries when using the TestKit runtime classpath are no longer usable in functional test code. Any external dependency
required by the test code needs to be declared for the test classpath.

### Changes to model rules DSL

- Properties and methods from owner closures are no longer visible.

### Changes to incubating software model

- `BinarySpec.name` should no longer be considered a unique identifier for the binary within a project.
- The name for the 'build' task for a binary is now qualified with the name of its component. For example, `jar` in `mylib` will have a build task called 'mylibJar'
- The name for the compile tasks for a binary is now qualified with the name of its component.
- JVM libraries have a binary called `jar` rather than one qualified with the library name.
- When building a JVM library with multiple variants, the task and output directory names have changed. The library name is now first.
- The top-level `binaries` container is now a `ModelMap` instead of a `DomainObjectContainer`. It is still accessible as `BinaryContainer`.
- `ComponentSpec.sources` and `BinarySpec.sources` now have true `ModelMap` semantics. Elements are created and configured on demand, and appear in the model report.
- It is no longer possible to configure `BinarySpec.sources` from the top-level `binaries` container: this functionality will be re-added in a subsequent release.
- `FunctionalSourceSet` is now a subtype of `ModelMap`, and no longer extends `Named`
- The implementation object of a `ComponentSpec`, `BinarySpec` or `LanguageSourceSet`, if defined, is no longer visible. These elements can only be accessed using their
public types or internal view types.

### Changes to incubating native software model

- Task names have changed for components with multiple variants. The library or executable name is now first.
- `org.gradle.language.PreprocessingTool` has moved to `org.gradle.nativeplatform.PreprocessingTool`
- TODO: Changes to discovered inputs/include paths

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Ethan Hall](https://github.com/ethankhall) - Addition of new `buildEnvironment` task.
* [Sebastian Schuberth](https://github.com/sschuberth) - Checkstyle HTML report.
* [Jeffry Gaston](https://github.com/mathjeff) - Debug message improvement.
* [Chun Yang](https://github.com/chunyang) - Play resources now properly maintain directory hierarchy.
* [Alexander Shoykhet](https://github.com/ashoykh) - Performance improvement for executing finalizer tasks with many dependencies.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
