/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.commons.test

import com.sun.net.httpserver.*
import java.net.InetSocketAddress
import java.nio.file.*
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.io.path.isRegularFile
import org.pkl.commons.createParentDirectories
import org.pkl.commons.deleteRecursively

/**
 * A test HTTP server that serves the Pkl packages defined under
 * `pkl-commons-test/src/main/files/packages`.
 *
 * To use this server from a test,
 * 1. Instantiate the server.
 * 2. (optional) Store the server in a companion or instance field.
 * 3. When setting up your test, pass the server [port] to one of the following:
 *     * `HttpClient.Builder.setTestPort`
 *     * `CliBaseOptions` constructor
 *     * `ExecutorOptions` constructor
 *     * `testPort` Gradle property
 *
 *   If the server isn't already running, it is automatically started.
 * 4. Use port `12110` in your test. `HttpClient` will replace this port with the server port.
 * 4. [Close][close] the server, for example in [AfterAll][org.junit.jupiter.api.AfterAll].
 */
class PackageServer : AutoCloseable {
  companion object {
    fun populateCacheDir(cacheDir: Path) {
      val basePath = cacheDir.resolve("package-1/localhost:$PORT")
      basePath.deleteRecursively()
      Files.walk(packagesDir).use { stream ->
        stream.forEach { source ->
          if (!source.isRegularFile()) return@forEach
          val relativized =
            source.toString().replaceFirst(packagesDir.toString(), "").drop(1).ifEmpty {
              return@forEach
            }
          val dest = basePath.resolve(relativized)
          dest.createParentDirectories()
          Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }

    // Port declared in tests.
    // Modified by RequestRewritingClient if testPort is set.
    private const val PORT = 12110

    // When tests are run via Gradle (i.e. from ./gradlew check), resources are packaged into a jar.
    // When run directly in IntelliJ, resources are just directories.
    private val packagesDir: Path by lazy {
      val uri = PackageServer::class.java.getResource("packages")!!.toURI()
      try {
        Path.of(uri)
      } catch (e: FileSystemNotFoundException) {
        FileSystems.newFileSystem(uri, mapOf<String, String>())
        Path.of(uri)
      }
    }

    private val simpleHttpsConfigurator by lazy {
      val sslContext =
        SSLContext.getInstance("SSL").apply {
          val pass = "password".toCharArray()
          val keystore = PackageServer::class.java.getResource("/localhost.p12")!!
          val ks = KeyStore.getInstance("PKCS12").apply { load(keystore.openStream(), pass) }
          val kmf = KeyManagerFactory.getInstance("SunX509").apply { init(ks, pass) }
          init(kmf.keyManagers, null, null)
        }
      val engine = sslContext.createSSLEngine()
      object : HttpsConfigurator(sslContext) {
        override fun configure(params: HttpsParameters) {
          params.needClientAuth = false
          params.cipherSuites = engine.enabledCipherSuites
          params.protocols = engine.enabledProtocols
          params.setSSLParameters(sslContext.supportedSSLParameters)
        }
      }
    }
  }

  /** The ephemeral listening port of this server. Automatically starts the server if necessary. */
  val port: Int by lazy {
    with(server.value) {
      bind(InetSocketAddress(0), 0)
      start()
      address.port
    }
  }

  /** Closes this server. */
  override fun close() {
    // don't start server just to stop it
    if (server.isInitialized()) {
      server.value.stop(0)
    }
  }

  private val server: Lazy<HttpsServer> = lazy {
    HttpsServer.create().apply {
      httpsConfigurator = simpleHttpsConfigurator
      createContext("/", handler)
      executor = Executors.newFixedThreadPool(1)
    }
  }

  private val handler = HttpHandler { exchange ->
    if (exchange.requestMethod != "GET") {
      exchange.sendResponseHeaders(405, 0)
      exchange.close()
      return@HttpHandler
    }
    val path = exchange.requestURI.path
    val localPath =
      if (path.endsWith(".zip")) packagesDir.resolve(path.drop(1))
      else packagesDir.resolve("${path.drop(1)}${path}.json")
    if (!Files.exists(localPath)) {
      exchange.sendResponseHeaders(404, 0)
      exchange.close()
      return@HttpHandler
    }
    exchange.sendResponseHeaders(200, 0)
    exchange.responseBody.use { outputStream -> Files.copy(localPath, outputStream) }
    exchange.close()
  }
}
