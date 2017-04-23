/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.http.internal

import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.gradle.api.UncheckedIOException
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.http.HttpBuildCache
import org.gradle.internal.resource.transport.http.DefaultSslContextFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpResourceInteraction
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpBuildCacheServiceTest extends Specification {
    public static final List<Integer> FATAL_HTTP_ERROR_CODES = [
        HttpStatus.SC_USE_PROXY,
        HttpStatus.SC_BAD_REQUEST,
        HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
        HttpStatus.SC_METHOD_NOT_ALLOWED,
        HttpStatus.SC_NOT_ACCEPTABLE, HttpStatus.SC_LENGTH_REQUIRED, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, HttpStatus.SC_EXPECTATION_FAILED,
        426, // Upgrade required
        HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
        511 // network authentication required
    ]

    @Rule HttpServer server = new HttpServer()
    @Rule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    BuildCacheService cache

    def key = new BuildCacheKey() {
        @Override
        String getHashCode() {
            return '0123456abcdef'
        }

        @Override
        String toString() {
            return getHashCode()
        }
    }

    def setup() {
        server.start()
        def config = new HttpBuildCache()
        config.url = server.uri.resolve("/cache/")
        cache = new DefaultHttpBuildCacheServiceFactory(new DefaultSslContextFactory()).createBuildCacheService(config)
    }

    def "can cache artifact"() {
        def destFile = tempDir.file("cached.zip")
        server.expectPut("/cache/${key.hashCode}", destFile)

        when:
        cache.store(key) { output ->
            output << "Data"
        }
        then:
        destFile.text == "Data"
    }

    def "can cache artifact with redirect"() {
        def destFile = tempDir.file("cached.zip")
        server.expectPutRedirected("/cache/${key.hashCode}","/redirect/cache/${key.hashCode}")
        server.expectPut("/redirect/cache/${key.hashCode}", destFile)

        when:
        cache.store(key) { output ->
            output << "Data"
        }
        then:
        destFile.text == "Data"
    }

    def "can load artifact from cache"() {
        def srcFile = tempDir.file("cached.zip")
        srcFile.text = "Data"
        server.expectGet("/cache/${key.hashCode}", srcFile)

        when:
        def receivedInput = null
        cache.load(key) { input ->
            receivedInput = input.text
        }

        then:
        receivedInput == "Data"
    }

    def "can load artifact from cache through redirect"() {
        def srcFile = tempDir.file("cached.zip")
        srcFile.text = "Data"
        server.expectGetRedirected("/cache/${key.hashCode}", "/redirect/cache/${key.hashCode}")
        server.expectGet("/redirect/cache/${key.hashCode}", srcFile)

        when:
        def receivedInput = null
        cache.load(key) { input ->
            receivedInput = input.text
        }

        then:
        receivedInput == "Data"
    }

    def "reports cache miss on 404"() {
        server.expectGetMissing("/cache/${key.hashCode}")

        when:
        def fromCache = cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        ! fromCache
    }

    def "load reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        BuildCacheException exception = thrown()

        exception.message == "Loading entry from '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << [HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_SERVICE_UNAVAILABLE]
    }

    def "load reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Loading entry from '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key) { output -> }

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Storing entry at '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key) { output -> }

        then:
        BuildCacheException exception = thrown()

        exception.message == "Storing entry at '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << [HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_SERVICE_UNAVAILABLE]
    }

    def "sends X-Gradle-Version and Content-Type headers on GET"() {
        server.expect("/cache/${key.hashCode}", ["GET"], new HttpServer.ActionSupport("get has appropriate headers") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                def gradleVersion = GradleVersion.version(request.getHeader("X-Gradle-Version"))
                assert gradleVersion == GradleVersion.current()

                def accept = request.getHeader(HttpHeaders.ACCEPT).split(", ")
                assert accept.length == 2
                assert accept[0] == HttpBuildCacheService.BUILD_CACHE_CONTENT_TYPE
                assert accept[1] == "*/*"

                response.setStatus(200)
            }
        })

        expect:
        cache.load(key) { input -> }
    }

    def "sends X-Gradle-Version and Content-Type headers on PUT"() {
        server.expect("/cache/${key.hashCode}", ["PUT"], new HttpServer.ActionSupport("put has appropriate headers") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                def gradleVersion = GradleVersion.version(request.getHeader("X-Gradle-Version"))
                assert gradleVersion == GradleVersion.current()

                assert request.getHeader(HttpHeaders.CONTENT_TYPE) == HttpBuildCacheService.BUILD_CACHE_CONTENT_TYPE

                response.setStatus(200)
            }
        })

        expect:
        cache.store(key) { output -> }
    }

    def "does preemptive authentication"() {
        def configuration = new HttpBuildCache()
        configuration.url = server.uri.resolve("/cache/")
        configuration.credentials.username = 'user'
        configuration.credentials.password = 'password'
        cache = new DefaultHttpBuildCacheServiceFactory(new DefaultSslContextFactory()).createBuildCacheService(configuration) as HttpBuildCacheService

        server.authenticationScheme = AuthScheme.BASIC

        def destFile = tempDir.file("cached.zip")
        destFile.text = 'Old'
        when:
        server.expectGet("/cache/${key.hashCode}", configuration.credentials.username, configuration.credentials.password, destFile)
        def result = null
        cache.load(key) { input ->
            result = input.text
        }
        then:
        result == 'Old'
        server.authenticationAttempts == ['Basic'] as Set

        server.expectPut("/cache/${key.hashCode}", configuration.credentials.username, configuration.credentials.password, destFile)

        when:
        cache.store(key) { output ->
            output << "Data"
        }
        then:
        destFile.text == "Data"
        server.authenticationAttempts == ['Basic'] as Set
    }

    private HttpResourceInteraction expectError(int httpCode, String method) {
        server.expect("/cache/${key.hashCode}", false, [method], new HttpServer.ActionSupport("return ${httpCode} broken") {
            @Override
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(httpCode, "broken")
            }
        })
    }
}
