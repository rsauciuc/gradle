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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class MavenPublishDependenciesIntegTest extends AbstractIntegrationSpec {

    public void "version range is mapped to maven syntax in published pom file"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile "group:projectA:latest.release"
                runtime "group:projectB:latest.integration"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
"""

        when:
        succeeds "publish"

        then:
        repoModule.assertPublishedAsJavaModule()
        repoModule.parsedPom.scopes.compile.expectDependency('group:projectA:RELEASE')
        repoModule.parsedPom.scopes.compile.expectDependency('group:projectB:LATEST')
    }

    @Issue("GRADLE-3233")
    @Unroll
    def "publishes POM dependency with #versionType version for Gradle dependency with null version"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile $dependencyNotation
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        repoModule.assertPublishedAsJavaModule()
        repoModule.parsedPom.scopes.compile.assertDependsOn("group:projectA:")
        def dependency = repoModule.parsedPom.scopes.compile.dependencies.get("group:projectA:")
        dependency.groupId == "group"
        dependency.artifactId == "projectA"
        dependency.version == ""

        where:
        versionType | dependencyNotation
        "empty"     | "'group:projectA'"
        "null"      | "group:'group', name:'projectA', version:null"
    }

    @Unroll("'#gradleConfiguration' dependencies end up in '#mavenScope' scope with '#plugin' plugin")
    void "maps dependencies in the correct Maven scope"() {
        given:
        def repoModule = mavenRepo.module('group', 'root', '1.0')

        file("settings.gradle") << '''
            rootProject.name = 'root' 
            include "b"
        '''
        buildFile << """
            apply plugin: "$plugin"
            apply plugin: "maven-publish"

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            
            dependencies {
                $gradleConfiguration project(':b')
            }
        """

        file('b/build.gradle') << """
            apply plugin: 'java'
            
            group = 'org.gradle.test'
            version = '1.2'
            
        """

        when:
        succeeds "publish"

        then:
        repoModule.assertPublishedAsJavaModule()
        repoModule.parsedPom.scopes."$mavenScope"?.expectDependency('org.gradle.test:b:1.2')

        where:
        plugin         | gradleConfiguration  | mavenScope
        'java'         | 'compile'            | 'compile'
        'java'         | 'runtime'            | 'compile'
        'java'         | 'implementation'     | 'runtime'
        'java'         | 'runtimeOnly'        | 'runtime'

        'java-library' | 'api'                | 'compile'
        'java-library' | 'compile'            | 'compile'
        'java-library' | 'runtime'            | 'compile'
        'java-library' | 'runtimeOnly'        | 'runtime'
        'java-library' | 'implementation'     | 'runtime'

    }

}
