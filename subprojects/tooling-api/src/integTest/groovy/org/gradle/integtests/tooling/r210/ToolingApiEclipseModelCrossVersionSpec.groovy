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

package org.gradle.integtests.tooling.r210
import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=2.10')
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=1.0-milestone-8 <2.10")
    def "older Gradle versions throw exception when querying Java source settings"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadEclipseProjectModel()
        rootProject.javaSourceSettings

        then:
        thrown(UnsupportedMethodException)
    }

    @TargetGradleVersion(">=2.10")
    def "non-Java projects return null for source settings"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings == null
    }

    @TargetGradleVersion(">=2.10")
    def "Java project returns default source compatibility"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "apply plugin: 'java'"

        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings.sourceLanguageLevel == org.gradle.api.JavaVersion.current()
    }

    @TargetGradleVersion(">=2.10")
    def "source language level is explicitly defined"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'java'
            sourceCompatibility = 1.6
        """

        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_6
    }

    @TargetGradleVersion(">=2.10")
    def "Multi-project build can define different source language level for subprojects"() {
        given:
        buildFile << """
            project(':subproject-a') {
                apply plugin: 'java'
                sourceCompatibility = 1.1
            }
            project(':subproject-b') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                eclipse {
                    jdt {
                        sourceCompatibility = 1.2
                    }
                }
            }
            project(':subproject-c') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                sourceCompatibility = 1.6
                eclipse {
                    jdt {
                        sourceCompatibility = 1.3
                    }
                }
            }
        """
        settingsFile << """
            rootProject.name = 'root'
            include 'subproject-a', 'subproject-b', 'subproject-c'
        """

        when:
        EclipseProject rootProject = loadEclipseProjectModel()
        EclipseProject subprojectA = rootProject.children.find { it.name == 'subproject-a' }
        EclipseProject subprojectB = rootProject.children.find { it.name == 'subproject-b' }
        EclipseProject subprojectC = rootProject.children.find { it.name == 'subproject-c' }

        then:
        subprojectA.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_1
        subprojectB.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        subprojectC.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
    }

    private EclipseProject loadEclipseProjectModel() {
        withConnection { connection -> connection.getModel(EclipseProject) }
    }
}
