import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import FilesTab from "./FilesTab";
import * as api from "./api";

vi.mock("./api", async importOriginal => ({
    ...(await importOriginal()), // keep the real MAX_UPLOAD_BYTES
    listFolders: vi.fn(),
    listItems: vi.fn(),
    fetchFile: vi.fn(),
}));

const LAB = {
    id: 7, uniqueKey: "lab1.py", type: "code", content: "lab1.py", tags: ["code", "python"],
    folderIds: [1], hasFile: true, originalFileName: "lab1.py", sizeBytes: 40, createdAt: "2026-11-16T10:00:00",
};

beforeEach(() => {
    vi.mocked(api.listFolders).mockReset().mockResolvedValue([{ id: 1, name: "CS 101", itemCount: 1 }]);
    vi.mocked(api.fetchFile).mockReset().mockReturnValue(new Promise(() => {})); // previews stay "loading"
    vi.mocked(api.listItems).mockReset().mockImplementation(async ({ query }) =>
        query ? [{ ...LAB, textMatch: "def quicksort(items):" }] : [LAB]);
});

it("searches as you type and highlights where the text inside a file matched", async () => {
    const user = userEvent.setup();
    render(<FilesTab />);
    expect(await screen.findByText("lab1.py", { selector: "strong" })).toBeInTheDocument();

    await user.type(screen.getByRole("searchbox", { name: "Search your files" }), "QuickSort");

    const mark = await screen.findByText("quicksort", { selector: "mark" });
    expect(mark).toBeInTheDocument();
    expect(screen.getByText("Found in file:")).toBeInTheDocument();
    // Waits for a pause in typing, so only the full word is searched for, not every letter.
    expect(api.listItems).toHaveBeenLastCalledWith({ query: "QuickSort", folderId: null });
    expect(api.listItems).not.toHaveBeenCalledWith({ query: "Q", folderId: null });
});
