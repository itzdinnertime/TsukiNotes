import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import UploadSection from "./UploadSection";
import * as api from "./api";

vi.mock("./api", async importOriginal => ({
    ...(await importOriginal()), // keep the real MAX_UPLOAD_BYTES
    uploadFile: vi.fn(),
}));

const FOLDERS = [
    { id: 1, name: "CS 101", itemCount: 0 },
    { id: 2, name: "Math 200", itemCount: 0 },
];

beforeEach(() => {
    vi.mocked(api.uploadFile).mockReset().mockResolvedValue({ id: 99 });
});

function renderUpload(props = {}) {
    const onUploaded = vi.fn();
    const view = render(<UploadSection folders={FOLDERS} currentFolderId={null} onUploaded={onUploaded} {...props} />);
    const input = view.container.querySelector('input[type="file"]');
    return { onUploaded, input };
}

describe("uploading a file", () => {
    it("won't upload until at least one folder is chosen", async () => {
        const user = userEvent.setup();
        const { input } = renderUpload();

        await user.upload(input, new File(["print(1)"], "lab.py"));
        await user.click(screen.getByRole("button", { name: "Upload" }));

        expect(screen.getByText("Choose at least one folder for this file")).toBeInTheDocument();
        expect(api.uploadFile).not.toHaveBeenCalled();
    });

    it("uploads into the chosen folders with the extra tags", async () => {
        const user = userEvent.setup();
        const { input, onUploaded } = renderUpload();

        await user.upload(input, new File(["print(1)"], "lab.py"));
        await user.click(screen.getByRole("button", { name: "CS 101" }));
        await user.click(screen.getByRole("button", { name: "Math 200" }));
        await user.type(screen.getByPlaceholderText(/Extra tags/), "homework, week1");
        await user.click(screen.getByRole("button", { name: "Upload" }));

        expect(api.uploadFile).toHaveBeenCalledWith(expect.any(File), "", ["homework", "week1"], [1, 2]);
        expect(onUploaded).toHaveBeenCalled();
    });

    it("pre-selects the folder that's open", async () => {
        const user = userEvent.setup();
        const { input } = renderUpload({ currentFolderId: 2 });

        await user.upload(input, new File(["notes"], "notes.txt"));

        expect(screen.getByRole("button", { name: /Math 200/ })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", { name: /CS 101/ })).toHaveAttribute("aria-pressed", "false");
    });

    it("rejects files over 10 MB before uploading", async () => {
        const user = userEvent.setup();
        const { input } = renderUpload();
        const big = new File(["x"], "huge.txt");
        Object.defineProperty(big, "size", { value: 11 * 1024 * 1024 });

        await user.upload(input, big);

        expect(screen.getByText("huge.txt is 11.0 MB. Files can be at most 10 MB.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Upload" })).not.toBeInTheDocument();
    });

    it("tells you to create a folder first when you have none", async () => {
        const user = userEvent.setup();
        const { input } = renderUpload({ folders: [] });

        await user.upload(input, new File(["x"], "a.txt"));

        expect(screen.getByText(/Every upload goes in a folder/)).toBeInTheDocument();
    });
});
