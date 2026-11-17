import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ItemCard from "./ItemCard";
import * as api from "./api";

vi.mock("./api");

const FOLDERS = [{ id: 1, name: "CS 101", itemCount: 1 }];

function note(overrides = {}) {
    return {
        id: 7, uniqueKey: "My note", type: "note", content: "hello", tags: ["todo"],
        folderIds: [], hasFile: false, createdAt: "2026-11-16T10:00:00", ...overrides,
    };
}

function renderCard(item, onChanged = vi.fn()) {
    render(<ul><ItemCard item={item} folders={FOLDERS} onChanged={onChanged} /></ul>);
    return onChanged;
}

beforeEach(() => {
    vi.mocked(api.deleteItem).mockReset().mockResolvedValue(null);
    vi.mocked(api.setItemFolders).mockReset().mockResolvedValue(null);
    vi.mocked(api.fetchFile).mockReset().mockReturnValue(new Promise(() => {})); // file previews stay "loading"
});

describe("deleting", () => {
    it("does nothing if you cancel the confirmation", async () => {
        const user = userEvent.setup();
        vi.spyOn(window, "confirm").mockReturnValue(false);
        renderCard(note());

        await user.click(screen.getByRole("button", { name: "Delete" }));

        expect(api.deleteItem).not.toHaveBeenCalled();
        expect(screen.getByText("My note")).toBeInTheDocument();
    });

    it("asks once, then removes the card straight away", async () => {
        const user = userEvent.setup();
        const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
        const onChanged = renderCard(note());

        await user.click(screen.getByRole("button", { name: "Delete" }));

        expect(confirm).toHaveBeenCalledTimes(1);
        expect(api.deleteItem).toHaveBeenCalledWith(7);
        expect(screen.queryByText("My note")).not.toBeInTheDocument();
        expect(onChanged).toHaveBeenCalled();
    });

    it("treats 'already deleted' as success, with no error shown", async () => {
        const user = userEvent.setup();
        vi.spyOn(window, "confirm").mockReturnValue(true);
        vi.mocked(api.deleteItem).mockRejectedValue(new Error("Item not found"));
        const onChanged = renderCard(note());

        await user.click(screen.getByRole("button", { name: "Delete" }));

        expect(screen.queryByText(/Couldn't delete/)).not.toBeInTheDocument();
        expect(onChanged).toHaveBeenCalled();
    });

    it("brings the card back with an error if deleting really failed", async () => {
        const user = userEvent.setup();
        vi.spyOn(window, "confirm").mockReturnValue(true);
        vi.mocked(api.deleteItem).mockRejectedValue(new Error("Failed to fetch"));
        const onChanged = renderCard(note());

        await user.click(screen.getByRole("button", { name: "Delete" }));

        expect(await screen.findByText("Couldn't delete: Failed to fetch")).toBeInTheDocument();
        expect(screen.getByText("My note")).toBeInTheDocument();
        expect(onChanged).not.toHaveBeenCalled();
    });
});

describe("moving between folders", () => {
    it("an uploaded file can't be taken out of its last folder", async () => {
        const user = userEvent.setup();
        renderCard(note({ hasFile: true, type: "code", folderIds: [1], originalFileName: "lab.py", sizeBytes: 10 }));

        await user.click(screen.getByRole("button", { name: /Move to folders/ }));
        await user.click(screen.getByRole("button", { name: /CS 101/ })); // un-tick its only folder
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(screen.getByText("Uploaded files must be in at least one folder")).toBeInTheDocument();
        expect(api.setItemFolders).not.toHaveBeenCalled();
    });

    it("a note can be taken out of every folder", async () => {
        const user = userEvent.setup();
        const onChanged = renderCard(note({ folderIds: [1] }));

        await user.click(screen.getByRole("button", { name: /Move to folders/ }));
        await user.click(screen.getByRole("button", { name: /CS 101/ }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(api.setItemFolders).toHaveBeenCalledWith(7, []);
        expect(onChanged).toHaveBeenCalled();
    });
});

describe("links", () => {
    it("opens safe links in a new tab", () => {
        renderCard(note({ type: "link", content: "https://example.edu/cs101" }));

        const link = screen.getByRole("link", { name: "https://example.edu/cs101" });
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("never turns a javascript: URL into a clickable link", () => {
        renderCard(note({ type: "link", content: "javascript:alert(1)" }));

        expect(screen.queryByRole("link")).not.toBeInTheDocument();
        expect(screen.getByText("javascript:alert(1)")).toBeInTheDocument();
    });
});

describe("display", () => {
    it("shows the item's folders and tags", () => {
        renderCard(note({ folderIds: [1] }));

        expect(screen.getByText("CS 101")).toBeInTheDocument();
        expect(screen.getByText("#todo")).toBeInTheDocument();
    });
});
