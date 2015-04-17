/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.build;

import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Informs about the Java environment, for example the Java home or the JVM args used.
 * See example in {@link BuildEnvironment}.
 *
 * @since 1.0-milestone-8
 */
public interface JavaEnvironment {

    /**
     * The Java home used for Gradle operations (for example running tasks or acquiring model information).
     *
     * @since 1.0-milestone-8
     */
    File getJavaHome();

    /**
     * The JVM arguments the user has provided to start the Java process that handles Gradle operations
     * (for example running tasks or acquiring model information).
     * The returned arguments do not include system properties passed as -Dfoo=bar.
     * They may include extra properties added by default if no user jvm arguments are specified,
     * like those required by the Gradle daemon (eg. MaxPermSize).
     *
     * @since 1.0-milestone-8
     */
    List<String> getJvmArguments();

    /**
     * The effective JVM arguments used to start the Java process that handles Gradle operations
     * (for example running tasks or acquiring model information).
     * The returned arguments do not include system properties passed as -Dfoo=bar.
     * They may include implicitly immutable system properties like "file.encoding".
     *
     * @since 2.5
     */
    @Incubating
    List<String> getEffectiveJvmArguments();

    /**
     * The effective JVM arguments used to start the Java process that handles Gradle operations
     * (for example running tasks or acquiring model information) including
     * system properties passed as -Dfoo=bar.
     * They may include implicitly immutable system properties like "file.encoding".
     *
     * @since 2.5
     */
    @Incubating
    List<String> getAllJvmArguments();

    /**
     * The system properties the user provided to start the Java process that handles Gradle operations
     * (for example running tasks or acquiring model information).
     *
     * @since 2.5
     */
    @Incubating
    Map<String,String> getSystemProperties();

    /**
     * The effective system properties used to start the Java process that handles Gradle operations
     * (for example running tasks or acquiring model information), including the default system properties.
     *
     * @since 2.5
     */
    @Incubating
    Map<String,String> getEffectiveSystemProperties();
}
