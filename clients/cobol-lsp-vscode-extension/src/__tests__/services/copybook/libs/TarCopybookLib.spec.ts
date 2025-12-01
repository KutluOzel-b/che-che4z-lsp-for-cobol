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
      const expectedValue = vscode.Uri.parse(
        `tar:///storage/tar/path/for/tar?filePath=APPLDICT/EMPRPT/RECORD/DEPARTMENT.CPY#COBOL`,
      );
      expect(JSON.stringify(result)).toBe(JSON.stringify(expectedValue));
    });
    it("copybook not present in the dataset", async () => {
      extApis.dsnService = new CopybookDownloaderForDsn(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      vscode.workspace.fs.readDirectory = jest.fn().mockResolvedValue([
        ["APPLDICT/EMPRPT/RECORD/DEPARTMENT1.CPY", vscode.FileType.File],
        ["SOME/RANDOM/FILE.CPY", vscode.FileType.File],
      ]);
      try {
        await dsnTarLib.resolveCopybookUri(
          "DEPARTMENT",
          vscode.Uri.file("/program.cbl"),
          DEFAULT_DIALECT,
        );
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("");
      }
    });
    it("dataset doesnt exists", () => {});
    it("invalid configuration check", () => {});
  });

  describe("listCopybooks", () => {
    describe("list copybook from dataset", () => {});
    describe("dataset doesn't exists", () => {});
  });
});
