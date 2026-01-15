import * as vscode from "vscode";
import { Memoize } from "../services/util/Memoize";
import {
  splitTarfilePath,
  TarContent,
  TarUtil,
} from "../services/util/TarUtil";

const reg = /tar:(.*?)\$\$/;
export const SEPARATOR = ":";
export class TarCopybookFileSystemProvider
  implements vscode.FileSystemProvider
{
  private emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
  public static readonly SCHEME = "cobol-ls-tar";
  public tarCache = new Memoize(
    async (tarFileUri: vscode.Uri) => {
      return await TarUtil.readTarFile(tarFileUri, this.emitter);
    },
    undefined,
    (tarFileUri: vscode.Uri) => {
      return `${TarCopybookFileSystemProvider.SCHEME}:${tarFileUri.fsPath}`;
    },
  );

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
    const { tarfilePath: tarfilePath, directory: directory } = splitTarfilePath(
      uri.fsPath,
    );
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
    const { tarfilePath: tarfilePath, directory: directory } = splitTarfilePath(
      uri.fsPath,
    );
    const tarfileUri = vscode.Uri.file(tarfilePath);
    const tarContent = await this.tarCache.execute(tarfileUri);
    if (!tarContent) {
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    }
    const result: [string, vscode.FileType][] = [];

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
    const { tarfilePath: tarfileUri, directory: directory } = splitTarfilePath(
      uri.fsPath,
    );

    const matchingFiles = await this.fetchMatchingFiles(
      tarfileUri,
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
    console.log(`file ${directory} not found in tar ${tarfileUri}`);
    throw vscode.FileSystemError.FileNotFound();
  }

  /**
   * clear cache
   */
  public clearCache() {
    for (const value of this.tarCache.getKeys()) {
      const fspath = value.match(reg);
      if (!fspath) continue;
      const uri = vscode.Uri.parse(fspath[1]);
      this.fireChange({ type: vscode.FileChangeType.Changed, uri: uri });
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
    const base = input.replace(/^\/|\/$/g, "");
    const baseDepth = base === "" ? 0 : base.split("/").length;
    const result: TarContent[] = [];

    for (const p of content) {
      const cleanPath = p.fileName.replace(/^\/|\/$/g, "");

      if (!cleanPath.startsWith(base)) continue;

      const parts = cleanPath.split("/");

      if (parts.length <= baseDepth) continue;

      if (parts.length === baseDepth + 1) {
        result.push({ fileName: parts[baseDepth], fileData: p.fileData });
      }
    }
    return result;
  }
}
