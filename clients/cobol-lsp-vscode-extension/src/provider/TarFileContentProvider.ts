import * as vscode from "vscode";
import { Memoize } from "../services/util/Memoize";
import { TarUtil } from "../services/util/TarUtil";
import { Minimatch } from "minimatch";

export class TarFileContentProvider
  implements vscode.TextDocumentContentProvider
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
  onDidChange?: vscode.Event<vscode.Uri> | undefined;
  async provideTextDocumentContent(
    uri: vscode.Uri,
    _token: unknown,
  ): Promise<string | undefined> {
    const {
      tarfsPath,
      internalPath,
      probableCopybooks,
      copybookName,
      dialect,
    } = this.getDetailsFromTarUri(uri);
    const tarContent = await this.tarCache.execute(vscode.Uri.file(tarfsPath));
    if (!tarContent) {
      throw new Error(`Failed to retrieve content for URI: ${uri.toString()}`);
    }
    const matchingFiles = tarContent.filter((e) =>
      new Minimatch(internalPath + "/*", {
        nocase: true,
        dot: true,
      }).match(e.fileName),
    );

    const foundFile = matchingFiles.filter(
      (f) =>
        probableCopybooks.filter((c) =>
          f.fileName.toUpperCase().endsWith(c.toUpperCase()),
        ).length > 0,
    );

    if (foundFile && foundFile.length > 0 && foundFile[0].fileContent) {
      // return the first found file
      return foundFile[0].fileContent;
    }
    console.log(
      `file ${copybookName} not found in tar ${tarfsPath} at ${internalPath} for dialect ${dialect}`,
    );
    return undefined;
  }

  private getDetailsFromTarUri(uri: vscode.Uri) {
    const tarfsPath = uri.fsPath.substring(0, uri.fsPath.indexOf("/tar:"));
    const queryParams = this.parseQuery(new URLSearchParams(uri.query));
    const internalPath = queryParams.get("internalPath");
    const copybookName = queryParams.get("copybook");
    const extensions = queryParams.get("extensions")?.split(",") || [];
    const probableCopybooks = extensions.map((ext) => copybookName + ext);
    const dialect = uri.fragment || "COBOL";
    return {
      tarfsPath,
      internalPath,
      probableCopybooks,
      copybookName,
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

  public clearCache() {
    this.tarCache.clearCache();
  }
}
