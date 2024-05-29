package org.pkl.core.packages

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.deleteRecursively
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.core.http.HttpClient
import org.pkl.core.SecurityManagers
import org.pkl.core.module.PathElement
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class PackageResolversTest {
  abstract class AbstractPackageResolverTest {

    abstract val resolver: PackageResolver

    private val packageRoot = FileTestUtils.rootProjectDir.resolve("pkl-commons-test/src/main/files/packages")

    companion object {
      private val packageServer = PackageServer()
      
      @JvmStatic
      @AfterAll
      fun afterAll() {
        packageServer.close()
      }
      
      val httpClient: HttpClient by lazy {
        HttpClient.builder()
          .addCertificates(FileTestUtils.selfSignedCertificate)
          .setTestPort(packageServer.port)
          .build()
      }
    }

    @Test
    fun `get module bytes`() {
      val expectedBirdModule = packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readString(StandardCharsets.UTF_8)
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/Bird.pkl")
      val birdModule = resolver
        .getBytes(assetUri, false, null)
        .toString(StandardCharsets.UTF_8)
      assertThat(birdModule).isEqualTo(expectedBirdModule)
    }

    @Test
    fun `get directory`() {
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/")
      val err = assertThrows<IOException> {
        resolver
          .getBytes(assetUri, false, null)
          .toString(StandardCharsets.UTF_8)
      }
      assertThat(err).hasMessage("Is a directory")
    }

    @Test
    fun `get directory, allowing directory reads`() {
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/")
      val bytes = resolver
        .getBytes(assetUri, true, null)
        .toString(StandardCharsets.UTF_8)
      assertThat(bytes).isEqualTo("""
        Bird.pkl
        allFruit.pkl
        catalog
        catalog.pkl
        some

      """.trimIndent())
    }

    @Test
    fun `get module bytes resolving path`() {
      val expectedBirdModule = packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readString(StandardCharsets.UTF_8)
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/foo/../Bird.pkl")
      val birdModule = resolver
        .getBytes(assetUri, false, null)
        .toString(StandardCharsets.UTF_8)
      assertThat(birdModule).isEqualTo(expectedBirdModule)
    }

    @Test
    fun `list path elements at root`() {
      // cast to set to avoid sort issues
      val elements = resolver
        .listElements(PackageAssetUri("package://localhost:0/birds@0.5.0#/"), null)
        .toSet()
      assertThat(elements).isEqualTo(
        setOf(
          PathElement("some", true),
          PathElement("catalog", true),
          PathElement("Bird.pkl", false),
          PathElement("allFruit.pkl", false),
          PathElement("catalog.pkl", false)
        )
      )
    }

    @Test
    fun `get multiple assets`() {
      val bird = resolver.getBytes(
        PackageAssetUri("package://localhost:0/birds@0.5.0#/Bird.pkl"),
        false,
        null
      )
      val swallow = resolver.getBytes(
        PackageAssetUri("package://localhost:0/birds@0.5.0#/catalog/Swallow.pkl"),
        false,
        null
      )
      assertThat(bird).isEqualTo(packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readBytes())
      assertThat(swallow).isEqualTo(packageRoot.resolve("birds@0.5.0/package/catalog/Swallow.pkl").readBytes())
    }

    @Test
    fun `list path elements in nested directory`() {
      // cast to set to avoid sort issues
      val elements = resolver.listElements(PackageAssetUri("package://localhost:0/birds@0.5.0#/catalog/"), null).toSet()
      assertThat(elements).isEqualTo(
        setOf(
          PathElement("Ostritch.pkl", false),
          PathElement("Swallow.pkl", false),
        )
      )
    }

    @Test
    fun `getBytes() throws FileNotFound if package exists but path does not`() {
      assertThrows<FileNotFoundException> {
        resolver
          .getBytes(
            PackageAssetUri("package://localhost:0/birds@0.5.0#/Horse.pkl"),
            false,
            null
          )
          .toString(StandardCharsets.UTF_8)
      }
    }

    @Test
    fun `getBytes() throws PackageLoadError if package does not exist`() {
      assertThrows<PackageLoadError> {
        resolver
          .getBytes(
            PackageAssetUri("package://localhost:0/not-a-package@0.5.0#/Horse.pkl"),
            false,
            null)
          .toString(StandardCharsets.UTF_8)
      }
    }

    @Test
    fun `requires package zip to be an HTTPS URI`() {
      assertThatCode {
        resolver.getBytes(
          PackageAssetUri("package://localhost:0/badPackageZipUrl@1.0.0#/Bug.pkl"),
          false,
          null)
      }
        .hasMessage("Expected the zip asset for package `package://localhost:0/badPackageZipUrl@1.0.0` to be an HTTPS URI, but got `ftp://wait/a/minute`.")
    }

    @Test
    fun `throws if package checksum is invalid`() {
      val error = assertThrows<PackageLoadError> { 
        resolver.getBytes(
          PackageAssetUri("package://localhost:0/badChecksum@1.0.0#/Bug.pkl"),
          false,
          null)
      }
      assertThat(error).hasMessageContaining("""
        Computed checksum: "a6bf858cdd1c09da475c2abe50525902580910ee5cc1ff624999170591bf8f69"
        Expected checksum: "intentionally bogus checksum"
      """.trimIndent())
    }
  }

  class DiskCachedPackageResolverTest : AbstractPackageResolverTest() {
    companion object {
      private val cacheDir = FileTestUtils.rootProjectDir.resolve("pkl-core/build/test-cache")

      @JvmStatic
      @AfterAll
      fun afterAll() {
        assertThat(cacheDir.exists())
        assertThat(cacheDir.listFilesRecursively()).isNotEmpty
      }

      @BeforeAll
      @JvmStatic
      fun beforeAll() {
        cacheDir.deleteRecursively()
      }
    }

    override val resolver: PackageResolver = PackageResolvers.DiskCachedPackageResolver(
      SecurityManagers.defaultManager, httpClient, cacheDir)
  }

  class InMemoryPackageResolverTest : AbstractPackageResolverTest() {
    override val resolver: PackageResolver = PackageResolvers.InMemoryPackageResolver(
      SecurityManagers.defaultManager, httpClient)
  }
}
