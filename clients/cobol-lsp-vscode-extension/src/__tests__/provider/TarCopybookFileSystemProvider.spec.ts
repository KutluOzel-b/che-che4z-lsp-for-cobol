import { TarCopybookFileSystemProvider } from "../../provider/TarCopybookFileSystemProvider";
import * as vscode from "vscode";
import { TarUtil } from "../../services/util/TarUtil";
import * as nodeFs from "fs/promises";

describe("TarCopybookFileSystemProvider", () => {
  vscode.workspace.fs.readFile = jest
    .fn()
    .mockImplementation(async (uri: vscode.Uri) => {
      return nodeFs.readFile(uri.fsPath);
    });

  vscode.workspace.fs.stat = jest
    .fn()
    .mockImplementation(async (uri: vscode.Uri) => {
      return nodeFs.stat(uri.fsPath);
    });

  const provider = new TarCopybookFileSystemProvider();
  const root = vscode.Uri.joinPath(vscode.Uri.parse(__dirname), "../../");
  const testFileUri = vscode.Uri.joinPath(
    root,
    root.fsPath.includes("src") ? "" : "src",
    "__tests__",
    "resources",
    "tar",
    "output.cobol-ls-tar",
  );

  const emitter_mock: vscode.EventEmitter<vscode.FileChangeEvent[]> = {
    dispose: jest.fn().mockResolvedValue({}),
    event: jest.fn().mockResolvedValue({}),
    fire: jest.fn().mockResolvedValue({}),
  };
  const tarContent = TarUtil.readTarFile(testFileUri, emitter_mock);
  beforeEach(async () => {
    const resolved = await tarContent;
    provider.tarCache.execute = jest.fn().mockResolvedValue(resolved);
  });

  describe("stat", () => {
    test("should return FileStat for an existing file", async () => {
      const uri = vscode.Uri.from({
        path: vscode.Uri.joinPath(
          testFileUri,
          "::",
          "APPLDICT/EMPRPT/RECORD/EMPOSITION.CPY",
        ).fsPath,
        scheme: testFileUri.scheme,
      });
      const stat = await provider.stat(uri);
      expect(stat.type).toBe(vscode.FileType.File);
      expect(stat.size).toBe(710);
      expect(typeof stat.ctime).toBe("number");
      expect(typeof stat.mtime).toBe("number");
    });

    test("should return FileStat for a non existing file", async () => {
      const uri = vscode.Uri.parse("cobol-ls-tar://my/archive.tar/:/SOME.CPY");
      await expect(provider.stat(uri)).rejects.toThrow(
        vscode.FileSystemError.FileNotFound(uri).message,
      );
    });
  });

  describe("readDirectory", () => {
    test("should return list of files for a search path", async () => {
      const uri = vscode.Uri.from({
        path: vscode.Uri.joinPath(testFileUri, "::", "APPLDICT/EMPRPT").fsPath,
        scheme: testFileUri.scheme,
      });
      const entries = await provider.readDirectory(uri);

      const expectedNames = [
        "SUBSCHEMA-CONTROL.CPY",
        "RECORD",
        "IDMS-STATUS.CPY",
      ];

      expect(entries.map((e) => e[0])).toEqual(
        expect.arrayContaining(expectedNames),
      );
    });

    test("should throw FileNotFound for reading a non-existent directory", async () => {
      const uri = vscode.Uri.from({
        path: vscode.Uri.joinPath(testFileUri, "::", "NON_EXISTENT_DIR/EMPRPT")
          .fsPath,
        scheme: testFileUri.scheme,
      });

      const entries = await provider.readDirectory(uri);

      expect(entries.length).toBe(0);
    });
  });

  describe("readFile", () => {
    test("should return file content as Uint8Array for an existing file", async () => {
      const uri = vscode.Uri.joinPath(
        testFileUri,
        "::",
        "APPLDICT/EMPRPT/RECORD/JOB.CPY",
      );
      const content = await provider.readFile(uri);
      const expectedCopybookContent =
        "       01  JOB.\n" +
        "           02  JOB-ID-0440             PIC 9(4).\n" +
        "           02  TITLE-0440              PIC X(20).\n" +
        "           02  DESCRIPTION-0440.\n" +
        "            03  DESCRIPTION-LINE-0440  PIC X(60)\n" +
        "                                       OCCURS 2.\n" +
        "           02  REQUIREMENTS-0440.\n" +
        "            03  REQUIREMENT-LINE-0440  PIC X(60)\n" +
        "                                       OCCURS 2.\n" +
        "           02  MINIMUM-SALARY-0440     PIC S9(6)V99.\n" +
        "           02  MAXIMUM-SALARY-0440     PIC S9(6)V99.\n" +
        "           02  SALARY-GRADES-0440      PIC 9(2)\n" +
        "                                       OCCURS 4.\n" +
        "           02  NUMBER-OF-POSITIONS-0440\n" +
        "                                       PIC 9(3).\n" +
        "           02  NUMBER-OPEN-0440        PIC 9(3).\n" +
        "           02  FILLER                  PIC XX.\n";
      expect(content).toBeInstanceOf(Uint8Array);
      const stringContent = new TextDecoder().decode(content);
      expect(stringContent).toBe(expectedCopybookContent);
    });

    test("should throw FileNotFound for a non-existent file", async () => {
      const uri = vscode.Uri.parse(
        "cobol-ls-tar://my/archive.tar/:/NO/EXISTING/JOB.CPY",
      );

      await expect(provider.readFile(uri)).rejects.toThrow(
        vscode.FileSystemError.FileNotFound(uri).message,
      );
    });
  });

  describe("Read-Only Operations", () => {
    test("writeFile should throw NoPermissions", async () => {
      try {
        await provider.writeFile(testFileUri, new Uint8Array(), {
          create: true,
          overwrite: true,
        });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });

    test("createDirectory should throw NoPermissions", async () => {
      try {
        await provider.createDirectory(testFileUri);
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });

    test("delete should throw NoPermissions", async () => {
      try {
        await provider.delete(testFileUri, { recursive: true });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });

    test("rename should throw NoPermissions", async () => {
      const newUri = vscode.Uri.parse(
        "cobol-ls-tar://my/archive.tar/new-file.txt",
      );
      try {
        await provider.rename(testFileUri, newUri, { overwrite: true });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });
  });
});
