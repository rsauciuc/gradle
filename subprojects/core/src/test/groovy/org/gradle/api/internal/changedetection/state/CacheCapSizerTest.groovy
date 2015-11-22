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

package org.gradle.api.internal.changedetection.state

import spock.lang.Specification


class CacheCapSizerTest extends Specification {

    def "cache cap sizer adjusts caps based on maximum heap size"() {
        given:
        def capSizer = new InMemoryTaskArtifactCache.CacheCapSizer(maxHeapMB)

        when:
        def caps = capSizer.calculateCaps()

        then:
        caps == expectedCaps

        where:
        maxHeapMB | expectedCaps
        100       | [taskArtifacts:400, outputFileStates:600, compilationState:200, fileHashes:80000, fileSnapshots:2000]
        200       | [taskArtifacts:400, outputFileStates:600, compilationState:200, fileHashes:80000, fileSnapshots:2000]
        768       | [taskArtifacts: 1600, outputFileStates: 2400, compilationState: 800, fileHashes: 325200, fileSnapshots: 8100]
        1024      | [taskArtifacts: 2300, fileHashes: 459900, compilationState: 1100, outputFileStates: 3400, fileSnapshots: 11500]
        1536      | [taskArtifacts: 3600, fileHashes: 729400, compilationState: 1800, outputFileStates: 5400, fileSnapshots: 18200]
        2048      | [taskArtifacts: 4900, fileHashes: 998900, compilationState: 2400, outputFileStates: 7400, fileSnapshots: 24900]
    }
}
