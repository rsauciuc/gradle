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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin

class JavaLanguageCustomLibraryDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def theModel(String model) {
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)
        buildFile << model
    }

    def "can depend on a custom component producing a JVM library"() {
        given:
        theModel '''
model {
    components {
        zdep(CustomLibrary)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJar)
                assert compileMainJarMainJava.classpath.files*.name == ['zdep.jar']
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'
    }

    @Unroll
    def "can depend on a custom component producing a JVM library in another project with dependency {#dependency}"() {
        given:
        applyJavaPlugin(buildFile)
        file('settings.gradle') << 'include "sub"'

        def subBuildFile = file('sub/build.gradle')
        subBuildFile << '''
plugins {
    id 'jvm-component'
}
'''
        addCustomLibraryType(subBuildFile)
        subBuildFile << '''
model {
    components {
        zdep(CustomLibrary)
    }
}
'''
        buildFile << """

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        $dependency
                    }
                }
            }
        }
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':sub:zdepJar')
                assert compileMainJarMainJava.classpath.files*.name == ['zdep.jar']
            }
        }
    }
}
"""
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'

        where:
        dependency << ["project ':sub' library 'zdep'", "project ':sub'"]
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can depend on a custom component producing a JVM library with corresponding platform"() {
        given:
        theModel '''
model {
    components {
        zdep(CustomLibrary) {
            javaVersions 6,7
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJava6Jar {
            doLast {
                assert compileMainJava6JarMainJava.taskDependencies.getDependencies(compileMainJava6JarMainJava).contains(zdep6Jar)
                assert compileMainJava6JarMainJava.classpath.files == [file("${buildDir}/jars/zdep6ApiJar/zdep.jar")] as Set
            }
        }
        mainJava7Jar {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(zdep7Jar)
                assert compileMainJava7JarMainJava.classpath.files == [file("${buildDir}/jars/zdep7ApiJar/zdep.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':mainJava6Jar', ':mainJava7Jar'

        then:
        executedAndNotSkipped ':tasks', ':mainJava6Jar', ':mainJava7Jar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should fail resolving dependencies only for the missing dependency variant"() {
        given:
        theModel '''
model {
    components {
        zdep(CustomLibrary) {
            javaVersions 7
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJava7Jar {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(zdepJar)
                assert compileMainJava7JarMainJava.classpath.files == [file("${buildDir}/jars/zdepApiJar/zdep.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when: 'The Java 7 variant of the main jar can be built'
        succeeds ':tasks', ':mainJava7Jar'

        then:
        executedAndNotSkipped ':tasks', ':mainJava7Jar'

        and: 'the Java 6 variant fails'
        fails ':mainJava6Jar'

        and: 'error message indicates the available platforms for the target dependency'
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'main:java6Jar'' source set 'Java source 'main:java''")
        failure.assertHasCause(TextUtil.normaliseLineSeparators("Cannot find a compatible variant for library 'zdep'.\n    Required platform 'java6', available: 'java7'"))
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest compatible platform variant of the target binary when dependency is a JVM component"() {
        given:
        theModel '''
model {
    components {
        zdep(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            targetPlatform 'java8'
        }
        main(CustomLibrary) {
            javaVersions 7
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJava7Jar)
                assert compileMainJarMainJava.classpath.files == [file("${buildDir}/jars/zdepJava7ApiJar/zdep.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest compatible platform variant of the target binary when dependency is a custom component"() {
        given:
        theModel '''
model {
    components {
        zdep(CustomLibrary) {
            javaVersions 6,7,8
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdep7Jar)
                assert compileMainJarMainJava.classpath.files == [file("${buildDir}/jars/zdep7ApiJar/zdep.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'
    }

    def "custom component can consume a JVM library"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec)
        zdep(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        zdepJar {
            doLast {
                assert compileZdepJarZdepJava.taskDependencies.getDependencies(compileZdepJarZdepJava).contains(mainJar)
                assert compileZdepJarZdepJava.classpath.files == [file("${buildDir}/jars/mainApiJar/main.jar")] as Set
            }
        }
    }
}
'''
        file('src/zdep/java/App.java') << 'public class App extends TestApp {}'
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks', ':zdepJar'

        then:
        executedAndNotSkipped ':tasks', ':zdepJar'
    }

    def "Java consumes custom component consuming Java component"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec)
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileMainJarMainJava.classpath.files == [file("${buildDir}/jars/secondApiJar/second.jar")] as Set

                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
                assert compileSecondJarSecondJava.classpath.files == [file("${buildDir}/jars/thirdApiJar/third.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'
    }

    def "Custom consumes Java component consuming custom component"() {
        given:
        theModel '''
model {
    components {
        main(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(CustomLibrary) {
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        mainJar {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileMainJarMainJava.classpath.files == [file("${buildDir}/jars/secondApiJar/second.jar")] as Set

                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
                assert compileSecondJarSecondJava.classpath.files == [file("${buildDir}/jars/thirdApiJar/third.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':mainJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Cannot build all variants of main component because of missing dependency variant"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            javaVersions 7
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java7'
        }
    }

    tasks {
        mainJava7Jar {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when: "Can resolve dependencies and compile the Java 7 variant of the main Jar"
        succeeds ':tasks', ':mainJava7Jar'

        then:
        executedAndNotSkipped ':tasks', ':mainJava7Jar'

        and: "Can resolve dependencies and compile any of the dependencies"
        succeeds ':secondJar'
        succeeds ':thirdJar'

        and: "Trying to compile the Java 6 variant fails"
        fails ':mainJava6Jar'
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'main:java6Jar'' source set 'Java source 'main:java''")
        failure.assertHasCause(TextUtil.normaliseLineSeparators("Cannot find a compatible variant for library 'second'.\n    Required platform 'java6', available: 'java7'"))
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Not all components target the same Java platforms"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            javaVersions 6,7
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java7'
        }
    }

    tasks {
        create('checkMainDependencies') {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(second7Jar)
                assert compileMainJava6JarMainJava.taskDependencies.getDependencies(compileMainJava6JarMainJava).contains(second6Jar)
            }
        }
        create('checkSecondJava7VariantDependencies') {
            doLast {
                assert compileSecond7JarSecondJava.taskDependencies.getDependencies(compileSecond7JarSecondJava).contains(thirdJar)
            }
        }
        create('checkSecondJava6VariantDependencies') {
            doLast {
                assert compileSecond6JarSecondJava.taskDependencies.getDependencies(compileSecond6JarSecondJava).empty
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "Can resolve dependencies of the Java 6 and Java 7 variant of the main Jar"
        succeeds ':checkMainDependencies'

        and: "Resolving the dependencies and compiling the Java 7 variant of the second jar should work"
        succeeds ':checkSecondJava7VariantDependencies'
        succeeds ':second7Jar'

        and: "Resolving the dependencies of the Java 6 version of the second jar should return an empty set"
        succeeds ':checkSecondJava6VariantDependencies'

        and: "Can build the Java 7 variant of all components"
        succeeds ':mainJava7Jar'
        succeeds ':second7Jar'
        succeeds ':thirdJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "All components should depend on the corresponding variants"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            javaVersions 6,7
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
    }

    tasks {
        mainJava6Jar {
            doLast {
                assert compileMainJava6JarMainJava.taskDependencies.getDependencies(compileMainJava6JarMainJava).contains(second6Jar)
                assert compileMainJava6JarMainJava.classpath.files == [file("${buildDir}/jars/second6ApiJar/second.jar")] as Set
            }
        }
        mainJava7Jar {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(second7Jar)
                assert compileMainJava7JarMainJava.classpath.files == [file("${buildDir}/jars/second7ApiJar/second.jar")] as Set
            }
        }
        second6Jar {
            doLast {
                assert compileSecond6JarSecondJava.taskDependencies.getDependencies(compileSecond6JarSecondJava).contains(thirdJava6Jar)
                assert compileSecond6JarSecondJava.classpath.files == [file("${buildDir}/jars/thirdJava6ApiJar/third.jar")] as Set
            }
        }
        second7Jar {
            doLast {
                assert compileSecond7JarSecondJava.taskDependencies.getDependencies(compileSecond7JarSecondJava).contains(thirdJava7Jar)
                assert compileSecond7JarSecondJava.classpath.files == [file("${buildDir}/jars/thirdJava7ApiJar/third.jar")] as Set
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "Can build the Java 7 variant of all components"
        succeeds ':mainJava7Jar'
        succeeds ':second7Jar'
        succeeds ':thirdJava7Jar'

        and: "Can build the Java 6 variant of all components"
        succeeds ':mainJava6Jar'
        succeeds ':second6Jar'
        succeeds ':thirdJava6Jar'
    }

    def "can define a cyclic dependency"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
                assert compileThirdJarThirdJava.taskDependencies.getDependencies(compileThirdJarThirdJava).contains(mainJar)
            }
        }
    }
}'''

        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp { void dependsOn(TestApp app) {} }'

        and: "Can resolve the dependencies for each component"
        succeeds ':checkDependencies'

        and: 'building fails'
        fails ':mainJar'
        failure.assertHasDescription 'Circular dependency between the following tasks:'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Fails if one of the dependencies provides more than one binary for the selected variant"() {
        given:
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            // duplication is intentional!
            javaVersions 6,6,7
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
    }

    tasks {
        create('checkJava7Dependencies') {
            doLast {
                assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(second7Jar)
                assert compileSecond7JarSecondJava.taskDependencies.getDependencies(compileSecond7JarSecondJava).contains(thirdJava7Jar)
            }
        }
        create('checkMainJava6Dependencies') {
            doLast {
                assert compileMainJava6JarMainJava.taskDependencies.getDependencies(compileMainJava6JarMainJava).empty
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "Can resolve dependencies of the Java 7 variant of the main and second components"
        succeeds ':checkJava7Dependencies'

        and: "Resolving the dependencies of the Java 6 variant of the main component should lead to an empty set"
        succeeds ':checkMainJava6Dependencies'

        and: "Can build the Java 7 variant of all components"
        succeeds ':mainJava7Jar'
        succeeds ':second7Jar'
        succeeds ':thirdJava7Jar'
    }

    def "complex graph of dependencies without variants"() {
        given:
        /*
                   +----------------+
                   |     main       |
                   |     (Java)     +-------------------------------------+
                   +--+-----------+-+                                     |
                      |           |                                       |
                      |           |                                       |
        +-------------v--+      +-v---------------+              +--------v----------+
        |     second     |      |      third      <--------------+     fourth        |
        |     (Java)     |      |      (Java)     |              |     (custom)      |
        +-------------+--+      +--+--------------+              +-------------------+
                      |            |
                      |            |
                   +--v------------v-+
                   |     fifth       |
                   |     (custom)    |
                   +-----------------+

         */
        theModel '''
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'second'
                        library 'third'
                        library 'fourth'
                    }
                }
            }
        }
        second(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'fifth'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'fifth'
                    }
                }
            }
        }
        fourth(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        fifth(CustomLibrary) {
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).containsAll([secondJar, thirdJar, fourthJar])
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(fifthJar)
                assert compileThirdJarThirdJava.taskDependencies.getDependencies(compileThirdJarThirdJava).contains(fifthJar)
                assert compileFourthJarFourthJava.taskDependencies.getDependencies(compileFourthJarFourthJava).contains(thirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app, ThirdApp app3, FourthApp app4) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(FifthApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp { void dependsOn(FifthApp app) {}  }'
        file('src/fourth/java/FourthApp.java') << 'public class FourthApp { void dependsOn(ThirdApp app) {} }'
        file('src/fifth/java/FifthApp.java') << 'public class FifthApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "can resolve dependencies"
        succeeds ':checkDependencies'

        and: "can build any of the components"
        succeeds ':mainJar'
        succeeds ':secondJar'
        succeeds ':thirdJar'
        succeeds ':fourthJar'
        succeeds ':fifthJar'
    }

    void addCustomLibraryType(File buildFile) {
        buildFile << '''
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.platform.base.internal.DefaultPlatformRequirement

interface CustomLibrary extends LibrarySpec {
    void javaVersions(int... platforms)
    List<Integer> getJavaVersions()
}

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {
    List<Integer> javaVersions = []
    void javaVersions(int... platforms) { platforms.each { javaVersions << it } }
}

            class ComponentTypeRules extends RuleSource {

                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }

                @ComponentBinaries
                void createBinaries(ModelMap<JarBinarySpec> binaries,
                    CustomLibrary library,
                    PlatformResolvers platforms,
                    @Path("buildDir") File buildDir,
                    JavaToolChainRegistry toolChains) {

                    def binariesDir = new File(buildDir, "jars")
                    def classesDir = new File(buildDir, "classes")
                    def javaVersions = library.javaVersions ?: [JavaVersion.current().majorVersion]
                    def multipleTargets = javaVersions.size() > 1
                    javaVersions.each { version ->
                        def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                        def toolChain = toolChains.getForPlatform(platform)
                        String binaryName = javaVersions.size() > 1 ? "${version}Jar" : "jar"
                        while (binaries.containsKey(binaryName)) { binaryName = "${binaryName}x" }
                        binaries.create(binaryName) { jar ->
                            jar.toolChain = toolChain
                            jar.targetPlatform = platform
                        }
                    }
                }

            }

            apply type: ComponentTypeRules
        '''
    }
}

