import { useState } from "react";
import SearchTab from "./SearchTab";
import FilesTab from "./FilesTab";

const TABS = [
    { id: "search", label: "Search" },
    { id: "files", label: "My files" },
];

export default function HomeScreen({ identity, onLogOut }) {
    const [activeTab, setActiveTab] = useState("search");

    return (
        <div>
            <header className="app-header">
                <div>
                    <h2>TsukiNotes</h2>
                    <span className="muted">Signed in as {identity.name}</span>
                </div>
                <button type="button" className="secondary" onClick={onLogOut}>Log out</button>
            </header>

            <nav className="tabs" role="tablist">
                {TABS.map(tab => (
                    <button
                        key={tab.id}
                        type="button"
                        role="tab"
                        id={`tab-${tab.id}`}
                        aria-selected={activeTab === tab.id}
                        aria-controls={`panel-${tab.id}`}
                        className={activeTab === tab.id ? "tab active" : "tab"}
                        onClick={() => setActiveTab(tab.id)}
                    >
                        {tab.label}
                    </button>
                ))}
            </nav>

            <div role="tabpanel" id={`panel-${activeTab}`} aria-labelledby={`tab-${activeTab}`}>
                {activeTab === "search" ? <SearchTab /> : <FilesTab />}
            </div>
        </div>
    );
}
