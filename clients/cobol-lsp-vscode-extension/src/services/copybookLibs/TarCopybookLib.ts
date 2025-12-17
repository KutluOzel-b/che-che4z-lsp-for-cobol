import { Uri } from "vscode";
import CopybookLib from "./CopybookLib";
import * as vscode from "vscode";
import { getProfileNameForCopybook } from "../util/ProfileUtils";
import { externalApis } from "../ExternalAPIsService";
import { LibDefinition } from "../ProcessorGroupsLoader";
import { TarCopybookFileSystemProvider } from "../../provider/TarCopybookFileSystemProvider";
import { SettingsService } from "../Settings";
import { getVariablesFromUri } from "../util/FSUtils";

export class TarCopybookLib implements CopybookLib {
  constructor(
    private locationType: "DSN" | "USS" | "local",
    private tarFileLocation: string,
    private searchPattern: string,
    private profile?: string,
  ) {}

  async resolveCopybookUri(
    copybookName: string,
    documentUri: Uri,
    dialect: string,
  ): Promise<Uri | (() => Promise<Uri | undefined>) | undefined> {
    const variables = getVariablesFromUri(documentUri, false);
    const evaluatedTarPath = SettingsService.evaluateVariables(
      this.tarFileLocation,
      variables,
    );
    let tarFileUri: vscode.Uri | undefined;
    if (this.locationType == "local") {
      const local = SettingsService.prepareLocalSearchUris(
        [evaluatedTarPath],
        vscode.workspace.workspaceFolders ?? [],
      );
      tarFileUri = local ? local[0] : undefined;
      if (!(tarFileUri && (await vscode.workspace.fs.stat(tarFileUri)))) return;
    } else {
      const effectiveExternalApi =
        this.locationType === "DSN"
          ? externalApis.dsnService
          : externalApis.ussService;

      const isAvailableAlready =
        await externalApis.isPresentLocally(evaluatedTarPath);
      tarFileUri = effectiveExternalApi?.getTarFileUri(this.tarFileLocation);
      if (!isAvailableAlready) {
        const profile = this.getProfile(documentUri);
        await externalApis.dsnService?.downloadFile(
          this.tarFileLocation,
          profile,
        );
      }
      if (!tarFileUri) return;
    }

    if (this.searchPattern) {
      this.searchPattern = SettingsService.evaluateVariables(
        this.searchPattern,
        variables,
      );
    }
    const matchingFilePath = await this.findMatchingFile(
      tarFileUri,
      dialect,
      documentUri,
      copybookName,
    );
    if (matchingFilePath)
      return vscode.Uri.from({
        scheme: TarCopybookFileSystemProvider.SCHEME,
        path: tarFileUri.fsPath,
        query: `filePath=${matchingFilePath}`,
        fragment: dialect,
      });
  }

  async listCopybooks(documentUri: Uri, dialect: string): Promise<string[]> {
    const variables = getVariablesFromUri(documentUri, false);
    const evaluatedTarPath = SettingsService.evaluateVariables(
      this.tarFileLocation,
      variables,
    );
    let tarFileUri: vscode.Uri | undefined;
    if (this.locationType == "local") {
      const local = SettingsService.prepareLocalSearchUris(
        [evaluatedTarPath],
        vscode.workspace.workspaceFolders ?? [],
      );
      tarFileUri = local ? local[0] : undefined;
      if (!(tarFileUri && (await vscode.workspace.fs.stat(tarFileUri))))
        return [];
    } else {
      const effectiveExternalApi =
        this.locationType === "DSN"
          ? externalApis.dsnService
          : externalApis.ussService;

      const isAvailableAlready =
        await externalApis.isPresentLocally(evaluatedTarPath);
      tarFileUri = effectiveExternalApi?.getTarFileUri(this.tarFileLocation);
      if (!isAvailableAlready || !tarFileUri) {
        return [];
      }
    }
    if (this.searchPattern) {
      this.searchPattern = SettingsService.evaluateVariables(
        this.searchPattern,
        variables,
      );
    }
    const directory = vscode.Uri.from({
      scheme: TarCopybookFileSystemProvider.SCHEME,
      path: tarFileUri?.fsPath,
      query: `searchPath=${this.searchPattern}`,
      fragment: dialect,
    });
    return vscode.workspace.fs
      .readDirectory(directory)
      .then((ele) => ele.map((e) => this.getFilenameFromPath(e[0])));
  }

  protected getProfile(documentUri: vscode.Uri) {
    return this.profile ?? getProfileNameForCopybook(documentUri) ?? "profile";
  }

  static create(config: LibDefinition) {
    if (
      typeof config === "object" &&
      "locationType" in config &&
      "tarFileLocation" in config
    ) {
      if ("profile" in config)
        return new TarCopybookLib(
          config["locationType"],
          config["tarFileLocation"],
          config["searchPattern"],
          config["profile"],
        );
      return new TarCopybookLib(
        config["locationType"],
        config["tarFileLocation"],
        config["searchPattern"],
      );
    }
  }

  private async findMatchingFile(
    tarFileUri: Uri,
    dialect: string,
    documentUri: Uri,
    copybookName: string,
  ) {
    const directory = vscode.Uri.from({
      scheme: TarCopybookFileSystemProvider.SCHEME,
      path: tarFileUri.fsPath,
      query: `searchPath=${this.searchPattern}`,
      fragment: dialect,
    });
    const allFiles = await vscode.workspace.fs
      .readDirectory(directory)
      .then((ele) => ele.map((e) => e[0]));

    const allowedExtensions = await SettingsService.getCopybookExtension(
      documentUri,
      dialect,
    );
    const probableCopybooks = new Set(
      allowedExtensions
        .map((e) => e.toUpperCase())
        .map((ext) => copybookName.toUpperCase() + ext),
    );
    const matchingFilePath = allFiles.find((file) => {
      const filename = this.getFilenameFromPath(file, true);
      if (probableCopybooks.has(filename.toUpperCase())) {
        return true;
      }
      return false;
    });
    return matchingFilePath;
  }

  private getFilenameFromPath(file: string, withExtension: boolean = false) {
    const lastSlashIndex = file.lastIndexOf("/");
    const filename =
      lastSlashIndex === -1 ? file : file.substring(lastSlashIndex + 1);
    return withExtension
      ? filename
      : filename.substring(0, filename.indexOf("."));
  }
}
