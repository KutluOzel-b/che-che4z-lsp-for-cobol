import * as vscode from "vscode";
import { TarCopybookLib } from "../../../../services/copybookLibs/TarCopybookLib";
import {
  ExternalAPIsService,
  initializeExternalAPIs,
} from "../../../../services/ExternalAPIsService";
import { DEFAULT_DIALECT } from "../../../../constants";
import { createZoweExplorerMock } from "../../../../__mocks__/getZoweExplorerMock.utility";
import { SettingsService } from "../../../../services/Settings";
import { getTarCached, TarContent } from "../../../../services/util/TarUtil";
import { CopybookBinaryDownloader } from "../../../../services/copybook/downloader/CopybookBinaryDownloader";

const emitter_mock: vscode.EventEmitter<vscode.FileChangeEvent[]> = {
  dispose: jest.fn().mockResolvedValue({}),
  event: jest.fn().mockResolvedValue({}),
  fire: jest.fn().mockResolvedValue({}),
};
const tarCache = getTarCached(emitter_mock);
const fileContent: TarContent = {
  fileName: "/APPLDICT/EMPRPT/RECORD/DEPARTMENT.CPY",
  fileData: {
    fileContent: ["content"],
    fileMetadata: {
      ctime: 0,
      mtime: 0,
      size: 0,
      type: vscode.FileType.File,
    },
  },
};
const tarContent: TarContent[] = [fileContent];
tarCache.execute = jest.fn().mockResolvedValue(tarContent);

beforeAll(async () => {
  await initializeExternalAPIs(
    vscode.Uri.file("/storage"),
    undefined,
    tarCache,
  );
});

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
      tarCache,
      "zeProfile",
    );
    it("copybook exists in tar", async () => {
      extApis.binaryDownloader = new CopybookBinaryDownloader(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );

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
      extApis.binaryDownloader = new CopybookBinaryDownloader(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );

      expect(
        await dsnTarLib.resolveCopybookUri(
          "DEPARTMENT1",
          vscode.Uri.file("/program.cbl"),
          DEFAULT_DIALECT,
        ),
      ).toBeFalsy();
    });
    it("local tar doesnt exists", async () => {
      const localTarLib = new TarCopybookLib(
        "local",
        "path/for/tar",
        "APPLDICT/EMPRPT/**",
        getTarCached(emitter_mock),
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
      extApis.binaryDownloader = new CopybookBinaryDownloader(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      extApis.binaryDownloader.downloadFile = jest.fn();
      extApis.binaryDownloader.isPresentLocally = jest
        .fn()
        .mockReturnValue(false);
      const dsnTarLib = new TarCopybookLib(
        "DSN",
        "path/for/tar",
        "APPLDICT/EMPRPT/**",
        tarCache,
        "zeProfile",
      );
      await dsnTarLib.resolveCopybookUri(
        "DEPARTMENT",
        vscode.Uri.file("/program.cbl"),
        DEFAULT_DIALECT,
      );
      expect(extApis.binaryDownloader.downloadFile).toHaveBeenCalledWith(
        "path/for/tar",
        "zeProfile",
        "DSN",
      );
    });
  });

  describe("listCopybooks", () => {
    it("list copybook from tar", async () => {
      extApis.binaryDownloader = new CopybookBinaryDownloader(
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
        tarCache,
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
  describe("Download cache", () => {
    it("clear cache calls downloader clear cache , deletes folder & recreates it", () => {
      extApis.binaryDownloader = new CopybookBinaryDownloader(
        vscode.Uri.file("/storage"),
        createZoweExplorerMock(),
      );
      extApis.clearCache();
      expect(vscode.workspace.fs.delete).toHaveBeenCalledTimes(1);
      expect(vscode.workspace.fs.delete).toHaveBeenCalledWith(
        vscode.Uri.file("/storage/tar"),
        { recursive: true },
      );
      expect(vscode.workspace.fs.createDirectory).toHaveBeenCalledWith(
        vscode.Uri.file("/storage/tar"),
      );
    });
  });
});
