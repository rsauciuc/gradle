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

package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Some information about the test having failed as part of running a build.
 *
 * @since 2.4
 */
@Incubating
public interface TestFailedEvent extends TestProgressEvent {

    /**
     * The description of the test having failed.
     *
     * @return The description
     */
    TestDescriptor getTestDescriptor();

    /**
     * The result of running the test with a failure.
     *
     * @return The result
     */
    TestFailure getTestResult();

}