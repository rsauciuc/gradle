/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.resources.ResourceException
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.transfer.DownloadBuildOperationDescriptor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildOperationExternalResourceTest extends Specification {

    @Shared
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Unroll
    def "delegates method call #methodName"() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)

        def resource = new BuildOperationExternalResource(buildOperationExecuter, delegate)

        when:
        resource."$methodName"()

        then:
        1 * delegate."$methodName"()

        where:
        methodName << ['getURI', 'getDisplayName', 'getMetaData', 'close', 'isLocal']
    }

    @Unroll
    def "wraps #methodSignature method call in a build operation"() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def uri = new URI("http://some/uri")
        def metaData = new DefaultExternalResourceMetaData(uri, 0, 1024)
        def operation = Mock(BuildOperationExecutor.Operation)
        def resource = new BuildOperationExternalResource(buildOperationExecuter, delegate)

        1 * delegate.getMetaData() >> metaData
        1 * buildOperationExecuter.run(_, _) >> { details, action ->
            invokeAction(action, Mock(BuildOperationContext))

            assert details.name == "Download http://some/uri"
            assert details.displayName == "Download http://some/uri"

            assert details.operationDescriptor instanceof DownloadBuildOperationDescriptor
            assert details.operationDescriptor.location == uri
            assert details.operationDescriptor.contentLength == 1024
            assert details.operationDescriptor.contentType == null
        }

        when:
        resource."$methodName"(parameter)

        then:
        1 * delegate."$methodName"(parameter)

        where:
        methodName    | parameter                            | methodSignature
        'writeTo'     | tmpDir.createFile("tempFile")        | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    @Unroll
    def "fails build operation if ResourceException is thrown in #methodSignature "() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def uri = new URI("http://some/uri")
        def metaData = new DefaultExternalResourceMetaData(uri, 0, 1024)
        def resource = new BuildOperationExternalResource(buildOperationExecuter, delegate)
        def buildOperationContext = Mock(BuildOperationContext)

        1 * delegate.getMetaData() >> metaData
        1 * buildOperationExecuter.run(_, _) >> { details, action ->
            invokeAction(action, buildOperationContext)
        }

        when:
        resource."$methodName"(parameter)

        then:
        thrown(ResourceException)

        1 * delegate."$methodName"(parameter) >> { throw new ResourceException("test resource exception") }

        where:
        methodName    | parameter                            | methodSignature
        'writeTo'     | tmpDir.createFile("tempFile")        | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    def invokeAction(Action action, BuildOperationContext buildOperationContext) {
        action.execute(buildOperationContext)
    }

    def invokeAction(def transformer, BuildOperationContext buildOperationContext) {
        transformer.transform(buildOperationContext)
    }

}
