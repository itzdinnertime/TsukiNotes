package com.tsukinotes.api.files;

import com.tsukinotes.api.files.FileClassifier.FileKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which uploads are allowed, how they're tagged, and how they're served back. No database needed. */
class FileClassifierTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final byte[] TEXT = "hello\n".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest(name = "{0} -> type {1}, tag {2}")
    @CsvSource({
            "notes.txt,   text, text",
            "README.md,   text, markdown",
            "main.py,     code, python",
            "App.JSX,     code, javascript",
            "app.tsx,     code, typescript",
            "Main.java,   code, java",
            "lib.rs,      code, rust",
            "query.sql,   code, sql",
            "page.html,   code, html",
            "config.yml,  code, yaml",
    })
    void textAndCodeFilesAreTaggedByExtension(String name, String type, String tag) {
        FileKind kind = FileClassifier.classify(name, TEXT);

        assertThat(kind.type()).isEqualTo(type);
        assertThat(kind.tag()).isEqualTo(tag);
        // Always plain text, so e.g. uploaded HTML is shown as code, never rendered as a page.
        assertThat(kind.contentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void realImagesAreAccepted() {
        FileKind png = FileClassifier.classify("photo.png", PNG);
        assertThat(png.type()).isEqualTo("image");
        assertThat(png.tag()).isEqualTo("image");
        assertThat(png.contentType()).isEqualTo("image/png");

        assertThat(FileClassifier.classify("a.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).contentType())
                .isEqualTo("image/jpeg");
        assertThat(FileClassifier.classify("a.gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)).contentType())
                .isEqualTo("image/gif");
        assertThat(FileClassifier.classify("a.webp", "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)).contentType())
                .isEqualTo("image/webp");
    }

    @Test
    void fileRenamedToLookLikeAnImageIsRejected() {
        assertRejected("fake.png", TEXT, "That file isn't a valid PNG image");
        assertRejected("fake.jpg", PNG, "That file isn't a valid JPG image");
    }

    @Test
    void binaryFileRenamedToLookLikeCodeIsRejected() {
        assertRejected("evil.py", new byte[]{0, 1, 2, 3}, "That file doesn't look like a text file");
        // Invalid UTF-8 (a lone continuation byte) is also not text.
        assertRejected("bad.txt", new byte[]{'a', (byte) 0x80, 'b'}, "That file doesn't look like a text file");
    }

    @Test
    void unsupportedTypesAreRejected() {
        String message = "Only images (PNG, JPG, GIF, WebP), text files and code files can be uploaded";
        assertRejected("tool.exe", "MZ".getBytes(StandardCharsets.US_ASCII), message);
        assertRejected("no-extension", TEXT, message);
        // SVG can contain scripts, so it's deliberately not allowed.
        assertRejected("drawing.svg", "<svg/>".getBytes(StandardCharsets.UTF_8), message);
    }

    private static void assertRejected(String name, byte[] bytes, String message) {
        assertThatThrownBy(() -> FileClassifier.classify(name, bytes))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
