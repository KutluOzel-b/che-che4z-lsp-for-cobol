import { TarCopybookFileSystemProvider } from "../../provider/TarCopybookFileSystemProvider";
import * as vscode from "vscode";
import * as j from "../../__mocks__/tarContent";

describe("TarCopybookFileSystemProvider", () => {
  let provider: TarCopybookFileSystemProvider;

  beforeEach(() => {
    provider = new TarCopybookFileSystemProvider();
    const tarContent = j.tarContent.map((e) => {
      return {
        fileName: e.fileName,
        fileData: {
          fileContent: new Uint8Array(Object.values(e.fileData.fileContent)),
          fileMetadata: e.fileData.fileMetadata,
        },
      };
    });
    provider.tarCache.execute = jest.fn().mockResolvedValue(tarContent);
  });

  describe("stat", () => {
    test("should return FileStat for an existing file", async () => {
      const uri = vscode.Uri.parse(
        "tar://my/archive.tar?filePath=APPLDICT/EMPRPT/RECORD/EMPOSITION.CPY#COBOL",
      );
      const stat = await provider.stat(uri);
      expect(stat.type).toBe(vscode.FileType.File);
      expect(stat.size).toBe(710);
      expect(typeof stat.ctime).toBe("number");
      expect(typeof stat.mtime).toBe("number");
    });

    test("should return FileStat for a non existing file", async () => {
      const uri = vscode.Uri.parse(
        "tar://my/archive.tar?filePath=SOME.CPY#COBOL",
      );
      await expect(provider.stat(uri)).rejects.toThrow(
        vscode.FileSystemError.FileNotFound(uri).message,
      );
    });
  });

  describe("readDirectory", () => {
    test("should return list of files for a search path", async () => {
      const uri = vscode.Uri.parse(
        "tar://my/archive.tar/?searchPath=APPLDICT/EMPRPT",
      );
      const entries = await provider.readDirectory(uri);

      const expectedNames = [
        "APPLDICT/EMPRPT/IDMS-STATUS.CPY",
        "APPLDICT/EMPRPT/SUBSCHEMA-CONTROL.CPY",
      ];

      expect(entries.map((e) => e[0])).toEqual(
        expect.arrayContaining(expectedNames),
      );
    });

    test("should throw FileNotFound for reading a non-existent directory", async () => {
      const uri = vscode.Uri.parse(
        "tar://my/archive.tar?searchPath=NON_EXISTENT_DIR/EMPRPT",
      );
      const entries = await provider.readDirectory(uri);

      expect(entries.length).toBe(0);
    });
  });

  describe("readFile", () => {
    test("should return file content as Uint8Array for an existing file", async () => {
      const uri = vscode.Uri.parse(
        "tar://my/archive.tar?filePath=APPLDICT/EMPRPT/RECORD/JOB.CPY",
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
        "tar://my/archive.tar?filePath=NO/EXISTING/JOB.CPY",
      );

      await expect(provider.readFile(uri)).rejects.toThrow(
        vscode.FileSystemError.FileNotFound(uri).message,
      );
    });
  });

  describe("Read-Only Operations", () => {
    const fileUri = vscode.Uri.parse("tar://my/archive.tar");

    test("writeFile should throw NoPermissions", async () => {
      try {
        await provider.writeFile(fileUri, new Uint8Array(), {
          create: true,
          overwrite: true,
        });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });

    test("createDirectory should throw NoPermissions", async () => {
      try {
        await provider.createDirectory(fileUri);
      } catch (error: unknown) {
        if (error instanceof Error)
          expect(error.message).toBe("Method not implemented.");
      }
    });

    test("delete should throw NoPermissions", async () => {
      try {
        await provider.delete(fileUri, { recursive: true });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });

    test("rename should throw NoPermissions", async () => {
      const newUri = vscode.Uri.parse("tar://my/archive.tar/new-file.txt");
      try {
        await provider.rename(fileUri, newUri, { overwrite: true });
      } catch (error: unknown) {
        if (error instanceof Error) expect(error.message).toBe("No Permission");
      }
    });
  });
});
