import * as vscode from "vscode";
import { TarCopybookLib } from "../../../../services/copybookLibs/TarCopybookLib";
import {
  ExternalAPIsService,
  initializeExternalAPIs,
} from "../../../../services/ExternalAPIsService";
import { DEFAULT_DIALECT } from "../../../../constants";
import { CopybookDownloaderForDsn } from "../../../../services/copybook/downloader/CopybookDownloaderForDsn";
import { createZoweExplorerMock } from "../../../../__mocks__/getZoweExplorerMock.utility";
import { SettingsService } from "../../../../services/Settings";

describe("Tar copybook lib tests", () => {
  let extApis: ExternalAPIsService;

  beforeEach(async () => {
    jest.clearAllMocks();
    extApis = await initializeExternalAPIs(vscode.Uri.file("/storage"));
  });

  describe("resolveCopybookUri", () => {
    SettingsService.getCopybookExtension = jest
      .fn()
      .mockReturnValue([".CPY", ".COPY"]);
    const dsnTarLib = new TarCopybookLib(
      "DSN",
      "path/for/tar",
      "APPLDICT/EMPRPT/**",
      "zeProfile",
    );
    it("copybook exists in tar", async () => {
      extApis.dsnService = new CopybookDownloaderForDsn(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      vscode.workspace.fs.readDirectory = jest.fn().mockResolvedValue([
        ["APPLDICT/EMPRPT/RECORD/DEPARTMENT.CPY", vscode.FileType.File],
        ["SOME/RANDOM/FILE.CPY", vscode.FileType.File],
      ]);
      const result = await dsnTarLib.resolveCopybookUri(
        "DEPARTMENT",
        vscode.Uri.file("/program.cbl"),
        DEFAULT_DIALECT,
      );
      expect(result).toBeTruthy();
      const expectedValue = vscode.Uri.from({
        scheme: "cobol-ls-tar",
        authority: "",
        path: "/storage/tar/path/for/tar/::/APPLDICT/EMPRPT/RECORD/DEPARTMENT.CPY",
      });
      expect(result instanceof vscode.Uri).toBeTruthy();
      expect((result as vscode.Uri).fsPath).toBe(expectedValue.fsPath);
    });
    it("copybook not present in the tar", async () => {
      extApis.dsnService = new CopybookDownloaderForDsn(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      vscode.workspace.fs.readDirectory = jest.fn().mockResolvedValue([
        ["APPLDICT/EMPRPT/RECORD/DEPARTMENT1.CPY", vscode.FileType.File],
        ["SOME/RANDOM/FILE.CPY", vscode.FileType.File],
      ]);
      expect(
        await dsnTarLib.resolveCopybookUri(
          "DEPARTMENT",
          vscode.Uri.file("/program.cbl"),
          DEFAULT_DIALECT,
        ),
      ).toBeFalsy();
    });
    it("local tar doesnt exists", async () => {
      extApis.isPresentLocally = jest.fn().mockReturnValue(false);
      const localTarLib = new TarCopybookLib(
        "local",
        "path/for/tar",
        "APPLDICT/EMPRPT/**",
        "zeProfile",
      );
      expect(
        await localTarLib.resolveCopybookUri(
          "DEPARTMENT",
          vscode.Uri.file("/program.cbl"),
          DEFAULT_DIALECT,
        ),
      ).toBeFalsy();
    });

    it("remote tar doesnt exists", async () => {
      extApis.dsnService = new CopybookDownloaderForDsn(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      extApis.dsnService.downloadFile = jest.fn();
      extApis.isPresentLocally = jest.fn().mockReturnValue(false);
      const dsnTarLib = new TarCopybookLib(
        "DSN",
        "path/for/tar",
        "APPLDICT/EMPRPT/**",
        "zeProfile",
      );
      await dsnTarLib.resolveCopybookUri(
        "DEPARTMENT",
        vscode.Uri.file("/program.cbl"),
        DEFAULT_DIALECT,
      );
      expect(extApis.dsnService.downloadFile).toHaveBeenCalledWith(
        "path/for/tar",
        "zeProfile",
      );
    });
  });

  describe("listCopybooks", () => {
    it("list copybook from tar", async () => {
      extApis.dsnService = new CopybookDownloaderForDsn(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      vscode.workspace.fs.readDirectory = jest.fn().mockResolvedValue([
        ["APPLDICT/EMPRPT/RECORD/DEPARTMENT.CPY", vscode.FileType.File],
        ["SOME/RANDOM/FILE.CPY", vscode.FileType.File],
      ]);
      const dsnTarLib = new TarCopybookLib(
        "DSN",
        "path/for/tar",
        "APPLDICT/EMPRPT/**",
        "zeProfile",
      );
      const result = await dsnTarLib.listCopybooks(
        vscode.Uri.file("/program.cbl"),
        DEFAULT_DIALECT,
      );
      expect(result.length).toBe(1);
      expect(result.includes("DEPARTMENT")).toBeTruthy();
    });
    describe("tar doesn't exists", () => {});
  });
});
