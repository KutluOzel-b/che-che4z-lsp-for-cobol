import * as vscode from "vscode";
import { Memoize } from "../services/util/Memoize";
import { splitTarfileUri, TarContent } from "../services/util/TarUtil";
import * as path from "path";

export const SEPARATOR = "::";
export class TarCopybookFileSystemProvider
  implements vscode.FileSystemProvider
{
  constructor(
    private tarCache: Memoize<[tarFileUri: vscode.Uri], TarContent[]>,
    private emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
  ) {}

  public static readonly SCHEME = "cobol-ls-tar";

  readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> =
    this.emitter.event;

  private fireChange(event: vscode.FileChangeEvent) {
    this.emitter.fire([event]);

    if (event.type === vscode.FileChangeType.Deleted) {
      this.clearCache();
    }
  }
  watch(
    _resource: vscode.Uri,
    _opts: { recursive: boolean; excludes: string[] },
  ): vscode.Disposable {
    return { dispose() {} };
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}${SEPERATOR}${pathWithinTar}`
   * @param uri
   * @returns
   */
  async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
    const { tarfilePath: tarfilePath, directory: directory } =
      splitTarfileUri(uri);
    const matchingFiles = await this.fetchMatchingFiles(
      tarfilePath,
      uri,
      directory,
    );
    if (matchingFiles && matchingFiles[0]) {
      return matchingFiles[0].fileData.fileMetadata;
    }
    throw vscode.FileSystemError.FileNotFound();
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}${SEPERATOR}${directoryPathWithinTar}`
   * get one level entries of a directory within TAR file
   * @param uri
   */
  async readDirectory(uri: vscode.Uri): Promise<[string, vscode.FileType][]> {
    const { tarfilePath: tarfilePath, directory: directory } =
      splitTarfileUri(uri);
    const tarfileUri = vscode.Uri.file(tarfilePath);
    const tarContent = await this.tarCache.execute(tarfileUri);
    if (!tarContent) {
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    }
    const result: [string, vscode.FileType][] = [];
    if (
      directory != "/" &&
      !tarContent.find(
        (x) =>
          x.fileData.fileMetadata.type === vscode.FileType.Directory &&
          (x.fileName.endsWith("/")
            ? x.fileName.slice(0, -1) === directory
            : x.fileName === directory),
      )
    )
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    const contents = this.getOneLevelChildren(tarContent, directory);
    contents.forEach((t) =>
      result.push([t.fileName, t.fileData.fileMetadata.type]),
    );
    return result;
  }

  createDirectory(_uri: unknown): void | Thenable<void> {
    throw vscode.FileSystemError.NoPermissions();
  }

  /**
   * `${TarFileContentProvider.SCHEME}://${tarFileUri.fsPath}${SEPERATOR}${filePathWitihTar}`
   * get contents of a file within TAR file with specified path
   * @param uri
   * @returns
   */
  async readFile(uri: vscode.Uri) {
    const { tarfilePath: tarfilePath, directory: directory } =
      splitTarfileUri(uri);

    const matchingFiles = await this.fetchMatchingFiles(
      tarfilePath,
      uri,
      directory,
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
    console.log(`file ${directory} not found in tar ${tarfilePath}`);
    throw vscode.FileSystemError.FileNotFound();
  }

  /**
   * clear cache
   */
  public clearCache() {
    for (const value of this.tarCache.getKeys()) {
      const uri = vscode.Uri.parse(value);
      this.fireChange({ type: vscode.FileChangeType.Deleted, uri: uri });
    }
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
    const matchingFiles =
      filePath == "/"
        ? this.getOneLevelChildren(tarContent, filePath)
        : tarContent.filter(
            (e) => e.fileName.toUpperCase() === filePath.toUpperCase(),
          );
    return matchingFiles;
  }

  writeFile(
    _uri: unknown,
    _content: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.NoPermissions();
  }
  delete(_uri: unknown, _options: unknown): void | Thenable<void> {
    throw vscode.FileSystemError.NoPermissions();
  }
  rename(
    _oldUri: unknown,
    _newUri: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.NoPermissions();
  }
  copy?(
    _source: unknown,
    _destination: unknown,
    _options: unknown,
  ): void | Thenable<void> {
    throw vscode.FileSystemError.NoPermissions();
  }

  private getOneLevelChildren(
    content: TarContent[],
    input: string,
  ): TarContent[] {
    const parent = path.normalize(input);
    const result: TarContent[] = [];

    for (const p of content) {
      const fullPath = path.normalize(p.fileName);

      const relative = path.relative(parent, fullPath);

      if (
        relative === "" ||
        relative.startsWith("..") ||
        path.isAbsolute(relative)
      ) {
        continue;
      }

      const parts = relative.split(path.sep);

      if (parts.length === 1) {
        result.push({
          fileName: parts[0],
          fileData: p.fileData,
        });
      }
    }

    return result;
  }
}
