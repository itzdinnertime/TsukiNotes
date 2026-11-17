import FilePreview from "./FilePreview";
import { FolderIcon } from "./FolderSection";
import { formatBytes } from "./format";

// Only allow http(s) URLs so a stored "javascript:..." value can't run when clicked.
function safeUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
    } catch {
        return null;
    }
}

function ItemContent({ type, content }) {
    const url = safeUrl(content);

    if (type === "image" && url) {
        return <img className="item-media" src={url} alt="" loading="lazy" />;
    }
    if (type === "video" && url) {
        return <video className="item-media" src={url} controls preload="metadata" />;
    }
    if (url) {
        return <a href={url} target="_blank" rel="noopener noreferrer">{content}</a>;
    }
    return <p className="item-text">{content}</p>;
}

export default function ItemCard({ item, folderNames = [] }) {
    const created = item.createdAt ? new Date(item.createdAt).toLocaleDateString() : null;
    const details = [item.hasFile && item.sizeBytes != null ? formatBytes(item.sizeBytes) : null, created]
        .filter(Boolean)
        .join(" · ");

    return (
        <li className="item-card">
            <div className="item-header">
                <strong>{item.uniqueKey}</strong>
                <span className="badge">{item.hasFile ? `${item.type} file` : item.type}</span>
            </div>
            {item.hasFile
                ? <FilePreview item={item} />
                : <ItemContent type={item.type} content={item.content} />}
            <div className="item-footer">
                <div className="tag-list">
                    {folderNames.map(name => (
                        <span key={`folder-${name}`} className="folder-tag"><FolderIcon />{name}</span>
                    ))}
                    {item.tags.map(tag => <span key={tag} className="tag">#{tag}</span>)}
                </div>
                {details && <span className="muted">{details}</span>}
            </div>
        </li>
    );
}
