import { Minimatch } from "minimatch";
import * as vscode from "vscode";
import { Memoize } from "../services/util/Memoize";
import { TarUtil } from "../services/util/TarUtil";

export class TarCopybookFileSystemProvider
  implements vscode.FileSystemProvider
{
  public static SCHEME = "tar";
  public tarCache = new Memoize(
    async (tarFileUri: vscode.Uri) => {
      return await TarUtil.readTarFile(tarFileUri);
    },
    undefined,
    (tarFileUri: vscode.Uri) => {
      const { tarfsPath, dialect } = this.getDetailsFromTarUri(tarFileUri);
      return `${tarfsPath}$$${dialect}`;
    },
  );

  onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> =
    new vscode.EventEmitter<vscode.FileChangeEvent[]>().event;
  watch(_uri: unknown, _options: unknown): vscode.Disposable {
    throw new Error("Method not implemented.");
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}?filePath=${evaluatedInternalPath}#${dialect}`
   * @param uri
   * @returns
   */
  async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
    const { tarfsPath, filePath } = this.getDetailsFromTarUri(uri);
    const matchingFiles = await this.fetchMatchingFiles(
      tarfsPath,
      uri,
      filePath,
    );
    if (matchingFiles && matchingFiles[0]) {
      return matchingFiles[0].fileData.fileMetadata;
    }
    throw vscode.FileSystemError.FileNotFound();
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}?searchPath=${evaluatedInternalPath}&extensions=${allowedExtensions.toString()}#${dialect}`
   * get list of all the files within a TAR file and search pattern
   * @param uri
   */
  async readDirectory(uri: vscode.Uri): Promise<[string, vscode.FileType][]> {
    const { tarfsPath, searchPath } = this.getDetailsFromTarUri(uri);
    const tarContent = await this.tarCache.execute(vscode.Uri.file(tarfsPath));
    if (!tarContent) {
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    }
    const result: [string, vscode.FileType][] = [];
    const matchingFiles = tarContent.filter((e) =>
      new Minimatch(searchPath + "/*", {
        nocase: true,
        dot: true,
      }).match(e.fileName),
    );
    matchingFiles.forEach((t) =>
      result.push([t.fileName, t.fileData.fileMetadata.type]),
    );
    return result;
  }

  createDirectory(_uri: unknown): void | Thenable<void> {
    throw new Error("Method not implemented.");
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}?filePath=${evaluatedInternalPath}#${dialect}`
   * @param uri
   * @returns
   */
  async readFile(uri: vscode.Uri) {
    const { tarfsPath, filePath, dialect } = this.getDetailsFromTarUri(uri);

    const matchingFiles = await this.fetchMatchingFiles(
      tarfsPath,
      uri,
      filePath,
    );

    if (
      matchingFiles &&
      matchingFiles.length > 0 &&
      matchingFiles[0].fileData &&
      matchingFiles[0].fileData.fileContent instanceof Uint8Array
    ) {
      // return the first found file
      return matchingFiles[0].fileData.fileContent;
    }
    console.log(
      `file ${filePath} not found in tar ${tarfsPath} for dialect ${dialect}`,
    );
    throw vscode.FileSystemError.FileNotFound();
  }

  /**
   * clear cache
   */
  public clearCache() {
    this.tarCache.clearCache();
  }

  private async fetchMatchingFiles(
    tarfsPath: string,
    uri: vscode.Uri,
    filePath: string | undefined,
  ) {
    const tarContent = await this.tarCache.execute(vscode.Uri.file(tarfsPath));
    if (!tarContent) {
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    }
    if (!filePath)
      throw vscode.FileSystemError.FileNotFound(
        "Need searchPath to locate file",
      );
    const matchingFiles = tarContent.filter(
      (e) => e.fileName.toUpperCase() === filePath.toUpperCase(),
    );
    return matchingFiles;
  }

  writeFile(
    _uri: unknown,
    _content: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.Unavailable();
  }
  delete(_uri: unknown, _options: unknown): void | Thenable<void> {
    throw vscode.FileSystemError.Unavailable();
  }
  rename(
    _oldUri: unknown,
    _newUri: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.Unavailable();
  }
  copy?(
    _source: unknown,
    _destination: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.Unavailable();
  }

  private getDetailsFromTarUri(uri: vscode.Uri) {
    const tarfsPath = uri.fsPath;
    const queryParams = this.parseQuery(new URLSearchParams(uri.query));
    const searchPath = queryParams.get("searchPath");
    const filePath = queryParams.get("filePath");
    const extensions = queryParams.get("extensions")?.split(",") || [];
    const dialect = uri.fragment || "COBOL";
    return {
      tarfsPath,
      searchPath,
      extensions,
      filePath,
      dialect,
    };
  }

  private parseQuery(params: URLSearchParams): Map<string, string> {
    const queryMap = new Map<string, string>();
    for (const [key, value] of params.entries()) {
      queryMap.set(key, value);
    }
    return queryMap;
  }
}
