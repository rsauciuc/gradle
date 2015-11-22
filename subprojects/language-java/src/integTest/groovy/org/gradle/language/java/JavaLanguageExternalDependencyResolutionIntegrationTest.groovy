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

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin

class JavaLanguageExternalDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def theModel(String model) {
        applyJavaPlugin(buildFile)
        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
        """
        buildFile << model
    }

    def setup() {
        file('src/main/java/TestApp.java') << 'public class TestApp {}'
    }

    def "can resolve dependency on library in maven repository"() {
        given:
        def module = mavenRepo.module("org.gradle", "test").publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'org.gradle:test:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps', Copy) {
                        into 'libs'
                        from compileMainJarMainJava.classpath
                    }
                }
            }
        """

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)
    }

    def "resolved classpath includes compile-scoped but not runtime-scoped transitive dependencies for library in maven repository"() {
        given:
        def compileDep = mavenRepo.module("org.gradle", "compileDep").publish()
        mavenRepo.module("org.gradle", "runtimeDep").publish()
        def module = mavenRepo.module("org.gradle", "test")
                .dependsOn("org.gradle", "compileDep", "1.0")
                .dependsOn("org.gradle", "runtimeDep", "1.0", null, "runtime")
                .publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'org.gradle:test:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps', Copy) {
                        into 'libs'
                        from compileMainJarMainJava.classpath
                    }
                }
            }
        """

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar', 'compileDep-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)
        file('libs/compileDep-1.0.jar').assertIsCopyOf(compileDep.artifactFile)
    }

    def "resolved classpath does not include transitive compile-scoped maven dependencies of local components"() {
        given:
        mavenRepo.module("org.gradle", "compileDep").publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'other'
                                }
                            }
                        }
                    }
                    other(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'org.gradle:compileDep:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps') {
                        dependsOn 'copyMainDeps'
                        dependsOn 'copyOtherDeps'
                    }
                    create('copyMainDeps', Copy) {
                        into 'mainLibs'
                        from compileMainJarMainJava.classpath
                    }
                    create('copyOtherDeps', Copy) {
                        into 'otherLibs'
                        from compileOtherJarOtherJava.classpath
                    }
                }
            }
        """
        file('src/other/java/Other.java')  << 'public class Other {}'

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar')
        file('otherLibs').assertHasDescendants('compileDep-1.0.jar')
    }

    def "resolved classpath includes transitive api-scoped dependencies of maven library dependency"() {
        given:
        mavenRepo.module("org.gradle", "compileDep").publish()
        mavenRepo.module("org.gradle", "transitiveDep").publish()
        mavenRepo.module("org.gradle", "transitiveApiDep").publish()
        mavenRepo.module("org.gradle", "apiDep")
                .dependsOn("org.gradle", "transitiveApiDep", "1.0", null, "compile")
                .dependsOn("org.gradle", "transitiveDep", "1.0", null, "runtime")
                .publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'other'
                                }
                            }
                        }
                    }
                    other(JvmLibrarySpec) {
                        api {
                            dependencies {
                                library 'org.gradle:apiDep:1.0'
                            }
                        }
                        sources {
                            java {
                                dependencies {
                                    library 'org.gradle:compileDep:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps', Copy) {
                        into 'mainLibs'
                        from compileMainJarMainJava.classpath
                    }
                }
            }
        """

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar', 'apiDep-1.0.jar', 'transitiveApiDep-1.0.jar')
    }

    def "reasonable error message when external dependency cannot be found"() {
        given:
        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        dependencies.library 'org.gradle:test:1.0'
                    }
                }
            }
        """

        expect:
        fails 'mainJar'

        and:
        failureDescriptionContains('Could not resolve all dependencies')
        failureCauseContains('Could not find org.gradle:test:1.0')
    }
}
