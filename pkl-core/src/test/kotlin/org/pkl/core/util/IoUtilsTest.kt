package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createParentDirectories
import org.pkl.commons.toPath
import org.pkl.core.SecurityManager
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeys
import org.pkl.core.runtime.ModuleResolver
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createFile

class IoUtilsTest {
  object FakeSecurityManager : SecurityManager {
    override fun checkResolveModule(uri: URI) {}

    override fun checkImportModule(importingModule: URI, importedModule: URI) {}

    override fun checkReadResource(resource: URI) {}

    override fun checkResolveResource(resource: URI) {}
  }

  private val moduleResolver = ModuleResolver(
    listOf(
      ModuleKeyFactories.pkg,
      ModuleKeyFactories.file,
      ModuleKeyFactories.standardLibrary,
      ModuleKeyFactories.genericUrl
    )
  )

  @Test
  fun `ensurePathEndsWithSlash() - relative URI`() {
    val uri = URI("/some/path")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri))
      .isEqualTo(URI("/some/path/"))

    val uri2 = URI("/some/path/")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri2))
      .isEqualTo(URI("/some/path/"))
  }

  @Test
  fun `ensurePathEndsWithSlash() - absolute URI`() {
    val uri = URI("https://apple.com/path")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri))
      .isEqualTo(URI("https://apple.com/path/"))

    val uri2 = URI("https://apple.com/path/")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri2))
      .isEqualTo(URI("https://apple.com/path/"))

    val uri3 = URI("https://user:pwd@apple.com:8080/path?foo=bar#frag")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri3))
      .isEqualTo(URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag"))

    val uri4 = URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri4))
      .isEqualTo(URI("https://user:pwd@apple.com:8080/path/?foo=bar#frag"))
  }

  @Test
  fun `ensurePathEndsWithSlash() - opaque URI`() {
    val uri = URI("foo:some.thing")
    assertThat(IoUtils.ensurePathEndsWithSlash(uri))
      .isEqualTo(URI("foo:some.thing"))
  }

  @Test
  fun `resolving relative URI against triple-slash file URI results in triple-slash file URI`() {
    val resolved = IoUtils.resolve(URI("file:///foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("file:///foo/baz"))
  }

  @Test
  fun `resolving relative URI against single-slash file URI results in single-slash file URI`() {
    val resolved = IoUtils.resolve(URI("file:/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("file:/foo/baz"))
  }
  
  @Test
  fun `resolving relative URI against triple-slash jar-file URI results in triple-slash jar-file URI`() {
    val resolved = IoUtils.resolve(URI("jar:file:///some/archive.zip!/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("jar:file:///some/archive.zip!/foo/baz"))
  }

  @Test
  fun `resolving relative URI against single-slash jar-file URI results in single-slash jar-file URI`() {
    val resolved = IoUtils.resolve(URI("jar:file:/some/archive.zip!/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("jar:file:/some/archive.zip!/foo/baz"))
  }
  
  @Test
  fun `resolve absolute URI against jar-file URI`() {
    val uri = URI("jar:file:///some/archive.zip!/foo/bar.pkl")
    assertThat(IoUtils.resolve(uri, URI("https://apple.com")))
      .isEqualTo(URI("https://apple.com"))
  }

  @Test
  fun `resolving other URIs works the same as java_net_URI_resolve()`() {
    val resolved = IoUtils.resolve(URI("https://apple.com/foo/bar"), URI("baz"))
    assertThat(resolved).isEqualTo(URI("https://apple.com/foo/baz"))

    val resolved2 = IoUtils.resolve(URI("test:opaque1"), URI("test:opaque2"))
    assertThat(resolved2).isEqualTo(URI("test:opaque2"))
  }

  @Test
  fun `relativize file URLs`() {
    // perhaps URI("") would be a more precise result
    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/bar/baz.pkl")
      )
    ).isEqualTo(URI("baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/bar/qux.pkl")
      )
    ).isEqualTo(URI("baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/bar/")
      )
    ).isEqualTo(URI("baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/bar")
      )
    ).isEqualTo(URI("bar/baz.pkl"))

    // URI.relativize() returns an absolute URI here
    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/qux/")
      )
    ).isEqualTo(URI("../bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/qux/qux2/")
      )
    ).isEqualTo(URI("../../bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://foo/qux/qux2")
      )
    ).isEqualTo(URI("../bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("file://qux/qux2/")
      )
    ).isEqualTo(URI("file://foo/bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("file://foo/bar/baz.pkl"),
        URI("https://foo/bar/baz.pkl")
      )
    ).isEqualTo(URI("file://foo/bar/baz.pkl"))
  }

  @Test
  fun `relativize HTTP URLs`() {
    // perhaps URI("") would be a more precise result
    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl"),
        URI("https://foo.com/bar/baz.pkl")
      )
    ).isEqualTo(URI("baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl"),
        URI("https://foo.com/bar/qux.pkl")
      )
    ).isEqualTo(URI("baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl"),
        URI("https://foo.com/qux/")
      )
    ).isEqualTo(URI("../bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl?query"),
        URI("https://foo.com/bar/qux.pkl")
      )
    ).isEqualTo(URI("baz.pkl?query"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl#fragment"),
        URI("https://foo.com/bar/qux.pkl")
      )
    ).isEqualTo(URI("baz.pkl#fragment"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl?query#fragment"),
        URI("https://foo.com/bar/qux.pkl")
      )
    ).isEqualTo(URI("baz.pkl?query#fragment"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com/bar/baz.pkl?query#fragment"),
        URI("https://foo.com/bar/qux.pkl?query2#fragment2")
      )
    ).isEqualTo(URI("baz.pkl?query#fragment"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com:80/bar/baz.pkl"),
        URI("https://foo.com:443/bar/")
      )
    ).isEqualTo(URI("https://foo.com:80/bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo.com:80/bar/baz.pkl"),
        URI("https://bar.com:80/bar/baz.pkl")
      )
    ).isEqualTo(URI("https://foo.com:80/bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo:bar@foo.com:80/bar/baz.pkl"),
        URI("https://foo:baz@bar.com:80/bar/baz.pkl")
      )
    ).isEqualTo(URI("https://foo:bar@foo.com:80/bar/baz.pkl"))

    assertThat(
      IoUtils.relativize(
        URI("https://foo/bar/baz.pkl"),
        URI("file://foo/bar/baz.pkl")
      )
    ).isEqualTo(URI("https://foo/bar/baz.pkl"))
  }

  @Test
  fun `isWhitespace()`() {
    assertThat(IoUtils.isWhitespace("")).isTrue
    assertThat(IoUtils.isWhitespace("  \t ")).isTrue
    assertThat(IoUtils.isWhitespace("  a ")).isFalse
  }

  @Test
  fun `toPath()`() {
    val uri = URI("file:///foo/bar.txt")
    assertThat(IoUtils.toPath(uri)).isEqualTo(uri.toPath())

    assertThat(IoUtils.toPath(URI("https://apple.com"))).isNull()
    assertThat(IoUtils.toPath(URI("unknown://foo/bar"))).isNull()
  }

  @Test
  fun `toPath() only accepts absolute URIs`() {
    assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
      IoUtils.toPath(URI("foo/bar"))
    }
  }

  @Test
  fun getMaxLineLength() {
    assertThat(IoUtils.getMaxLineLength("abc")).isEqualTo(3)
    assertThat(IoUtils.getMaxLineLength("abc\n\nabcd\n\nab")).isEqualTo(4)
  }

  @Test
  fun capitalize() {
    assertThat(IoUtils.capitalize("abc")).isEqualTo("Abc")
    assertThat(IoUtils.capitalize("Abc")).isEqualTo("Abc")
    assertThat(IoUtils.capitalize("a&*")).isEqualTo("A&*")
    assertThat(IoUtils.capitalize("_&*")).isEqualTo("_&*")
    assertThat(IoUtils.capitalize("abc def")).isEqualTo("Abc def")
    assertThat(IoUtils.capitalize("Abc def")).isEqualTo("Abc def")
  }

  @Test
  fun inferModuleName() {
    val assertions = mapOf(
      "file:///foo.pkl" to "foo",
      "file:///foo/bar/baz.pkl" to "baz",
      "jar:file:///some/archive.zip!/foo.pkl" to "foo",
      "jar:file:///some/archive.zip!/foo/bar/baz.pkl" to "baz",
      "https://apple.com/foo.pkl" to "foo",
      "https://apple.com/foo/bar/baz.pkl" to "baz",
      "pkl:foo" to "foo",
      "pkl:foo.bar.baz" to "baz",
      "modulepath:/foo.pkl" to "foo",
      "modulepath:/foo/bar/baz.pkl" to "baz",
      "package://example.com/foo/bar@1.0.0#/baz.pkl" to "baz",
      "package://example.com/foo/bar@1.0.0#/baz/biz/qux.pkl" to "qux"
    )
    for ((uriStr, name) in assertions) {
      val uri = URI(uriStr)
      val moduleKey = moduleResolver.resolve(uri)
      assertThat(IoUtils.inferModuleName(moduleKey)).isEqualTo(name)
    }
  }

  @Test
  fun toUri() {
    assertThat(IoUtils.toUri("file://foo.pkl")).isEqualTo(URI("file://foo.pkl"))
    assertThat(IoUtils.toUri("foo.pkl")).isEqualTo(URI("foo.pkl"))
    assertThat(IoUtils.toUri("foo bar.pkl").rawPath).isEqualTo("foo%20bar.pkl")
    assertThrows<URISyntaxException> {
      IoUtils.toUri("file:foo bar.pkl")
    }
  }

  @Test
  fun `resolveUri - file hierarchy`(@TempDir tempDir: Path) {
    val file1 = tempDir.resolve("base1/base2/foo.pkl").createParentDirectories().createFile()
    val file2 = tempDir.resolve("base1/base2/dir1/dir2/foo.pkl").createParentDirectories().createFile()
    val file3 = tempDir.resolve("base1/dir2/foo.pkl").createParentDirectories().createFile()

    val uri = file2.toUri()
    val key = ModuleKeys.file(uri, file2)

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI("..."))).isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../foo.pkl"))).isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../base2/foo.pkl"))).isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../base1/base2/foo.pkl"))).isEqualTo(file1.toUri())
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../dir2/foo.pkl"))).isEqualTo(file3.toUri())

    assertThrows<URISyntaxException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../"))
    }

    assertThrows<URISyntaxException> {
      IoUtils.resolve(FakeSecurityManager, key, URI("...abc"))
    }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../bar.pkl"))
    }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../base2/bar.pkl"))
    }

    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key, URI(".../foo/bar.pkl"))
    }
  }

  @Test
  fun `resolveUri - classpath hierarchy`() {
    val classLoader = this::class.java.classLoader
    val uri = URI("modulepath:/org/pkl/core/module/dir1/dir2/NamedModuleResolversTest.pkl")
    val key = ModuleKeys.classPath(uri, classLoader)
    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI("...")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../NamedModuleResolversTest.pkl")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../module/NamedModuleResolversTest.pkl")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../core/module/NamedModuleResolversTest.pkl")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    assertThat(IoUtils.resolve(FakeSecurityManager, key, URI(".../org/pkl/core/module/NamedModuleResolversTest.pkl")))
      .isEqualTo(URI("modulepath:/org/pkl/core/module/NamedModuleResolversTest.pkl"))

    val uri2 = URI("modulepath:/foo/bar/baz.pkl")
    val key2 = ModuleKeys.classPath(uri2, classLoader)
    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI("..."))
    }
    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI(".../other.pkl"))
    }
    assertThrows<FileNotFoundException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI(".../dir1/dir2/NamedModuleResolversTest.pkl"))
    }

    assertThrows<URISyntaxException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI(".../"))
    }
    assertThrows<URISyntaxException> {
      IoUtils.resolve(FakeSecurityManager, key2, URI("...NamedModuleResolversTest.pkl"))
    }
  }
  
  @Test
  fun `readBytes(URL) does not support HTTP URLs`() {
    assertThrows<IllegalArgumentException> {
      IoUtils.readBytes(URL("https://example.com"))
    }
    assertThrows<IllegalArgumentException> {
      IoUtils.readBytes(URL("http://example.com"))
    }
  }

  @Test
  fun `readString(URL) does not support HTTP URLs`() {
    assertThrows<IllegalArgumentException> {
      IoUtils.readString(URL("https://example.com"))
    }
    assertThrows<IllegalArgumentException> {
      IoUtils.readString(URL("http://example.com"))
    }
  }
}
