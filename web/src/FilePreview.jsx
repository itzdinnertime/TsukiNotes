import { useEffect, useState } from "react";
import { fetchFile } from "./api";

const PREVIEW_LINES = 20;

/** Shows an uploaded file: images inline, text and code as a preview, plus a download link. */
export default function FilePreview({ item }) {
    const [file, setFile] = useState(null); // { blob, url }
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelled = false;
        let objectUrl = null;
        fetchFile(item.id)
            .then(blob => {
                if (cancelled) return;
                objectUrl = URL.createObjectURL(blob);
                setFile({ blob, url: objectUrl });
            })
            .catch(err => { if (!cancelled) setError(err.message); });
        return () => {
            cancelled = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [item.id]);

    if (error) return <p className="error">Couldn't load file: {error}</p>;
    if (!file) return <p className="muted">Loading file…</p>;

    return (
        <>
            {item.type === "image"
                ? <img className="item-media" src={file.url} alt={item.originalFileName} />
                : <TextPreview blob={file.blob} />}
            <a className="download-link" href={file.url} download={item.originalFileName}>
                Download {item.originalFileName}
            </a>
        </>
    );
}

function TextPreview({ blob }) {
    const [text, setText] = useState(null);
    const [expanded, setExpanded] = useState(false);

    useEffect(() => {
        let cancelled = false;
        blob.text().then(t => { if (!cancelled) setText(t); });
        return () => { cancelled = true; };
    }, [blob]);

    if (text === null) return null;

    const lines = text.split("\n");
    const isLong = lines.length > PREVIEW_LINES;
    const shown = expanded || !isLong ? text : lines.slice(0, PREVIEW_LINES).join("\n");

    return (
        <div>
            <pre className="code-preview"><code>{shown}</code></pre>
            {isLong && (
                <button type="button" className="link-button" onClick={() => setExpanded(!expanded)}>
                    {expanded ? "Show less" : `Show all ${lines.length} lines`}
                </button>
            )}
        </div>
    );
}
