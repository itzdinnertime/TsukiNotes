import { useState } from "react";
import { searchItems } from "./api";
import ItemCard from "./ItemCard";

export default function SearchTab() {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState(null);
    const [error, setError] = useState("");
    const [searching, setSearching] = useState(false);

    async function handleSearch(e) {
        e.preventDefault();
        const tags = query.split(",").map(t => t.trim()).filter(Boolean);
        if (tags.length === 0) {
            setError("Enter at least one tag");
            return;
        }
        setSearching(true);
        try {
            setResults(await searchItems(tags));
            setError("");
        } catch (err) {
            setError(err.message);
        } finally {
            setSearching(false);
        }
    }

    return (
        <section>
            <form className="search-form" onSubmit={handleSearch}>
                <input
                    type="search"
                    value={query}
                    onChange={e => setQuery(e.target.value)}
                    placeholder="Search by tags, comma separated"
                    aria-label="Search by tags"
                />
                <button type="submit" disabled={searching}>{searching ? "Searching…" : "Search"}</button>
            </form>

            {error && <p className="error">{error}</p>}

            {results && results.length === 0 && (
                <p className="empty">No items have all of those tags.</p>
            )}
            {results && results.length > 0 && (
                <ul>
                    {results.map(item => <ItemCard key={item.id} item={item} />)}
                </ul>
            )}
        </section>
    );
}
