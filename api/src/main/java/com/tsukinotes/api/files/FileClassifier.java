package com.tsukinotes.api.files;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Decides what kind of file an upload is (image, text, or a specific kind of code), which tags it gets
 * automatically, and which Content-Type it's served back with. The decision is based on the extension
 * and then checked against the file's actual bytes, so a renamed binary can't sneak through.
 */
public final class FileClassifier {

    /** type: item type stored in the DB ("image", "text" or "code"); tag: language/kind tag; contentType: how it's served. */
    public record FileKind(String type, String tag, String contentType) {
    }

    private static final String TEXT_PLAIN = "text/plain; charset=UTF-8";

    // SVG is deliberately not allowed: it can contain scripts.
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    private static final Map<String, String> TEXT_EXTENSIONS = Map.of(
            "txt", "text",
            "md", "markdown");

    // Every code file is served as plain text, so even .html is displayed, never rendered.
    private static final Map<String, String> CODE_EXTENSIONS = Map.ofEntries(
            Map.entry("py", "python"),
            Map.entry("js", "javascript"),
            Map.entry("jsx", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("c", "c"),
            Map.entry("h", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("cc", "cpp"),
            Map.entry("hpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("swift", "swift"),
            Map.entry("lua", "lua"),
            Map.entry("sql", "sql"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("ps1", "powershell"),
            Map.entry("html", "html"),
            Map.entry("css", "css"),
            Map.entry("json", "json"),
            Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"),
            Map.entry("xml", "xml"));

    private FileClassifier() {
    }

    public static FileKind classify(String fileName, byte[] bytes) {
        String extension = extensionOf(fileName);

        String imageType = IMAGE_EXTENSIONS.get(extension);
        if (imageType != null) {
            if (!looksLikeImage(imageType, bytes)) {
                throw unsupported("That file isn't a valid " + extension.toUpperCase(Locale.ROOT) + " image");
            }
            return new FileKind("image", "image", imageType);
        }

        String textTag = TEXT_EXTENSIONS.get(extension);
        String codeTag = CODE_EXTENSIONS.get(extension);
        if (textTag != null || codeTag != null) {
            if (!isUtf8Text(bytes)) {
                throw unsupported("That file doesn't look like a text file");
            }
            return textTag != null
                    ? new FileKind("text", textTag, TEXT_PLAIN)
                    : new FileKind("code", codeTag, TEXT_PLAIN);
        }

        throw unsupported("Only images (PNG, JPG, GIF, WebP), text files and code files can be uploaded");
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeImage(String contentType, byte[] b) {
        return switch (contentType) {
            case "image/png" -> startsWith(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> startsWith(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/gif" -> startsWith(b, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(b, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> b.length >= 12
                    && startsWith(b, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(b, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private static boolean isUtf8Text(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false; // NUL bytes mean binary data
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static ResponseStatusException unsupported(String message) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
