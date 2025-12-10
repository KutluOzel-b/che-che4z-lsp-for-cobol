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
    const profile = this.getProfile(documentUri);
    const canDownloadTar = this.locationType !== "local";
    const effectiveExternalApi =
      this.locationType === "DSN"
        ? externalApis.dsnService
        : externalApis.ussService;
    const tarFileUri =
      this.locationType === "local"
        ? vscode.Uri.parse(this.tarFileLocation)
        : effectiveExternalApi?.getTarFileUri(this.tarFileLocation);
    const isUnderExtStorage = this.locationType !== "local";
    if (!tarFileUri) return;
    if (
      !(await externalApis?.isPresentLocally(tarFileUri, isUnderExtStorage)) &&
      canDownloadTar
    ) {
      await externalApis.dsnService?.downloadFile(
        this.tarFileLocation,
        profile,
      );
    }
    const variables = getVariablesFromUri(documentUri, false);
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
      return vscode.Uri.parse(
        `${TarCopybookFileSystemProvider.SCHEME}://${tarFileUri.fsPath}?filePath=${matchingFilePath}#${dialect}`,
      );
  }

  async listCopybooks(documentUri: Uri, dialect: string): Promise<string[]> {
    const effectiveExternalApi =
      this.locationType === "DSN"
        ? externalApis.dsnService
        : externalApis.ussService;

    const tarFileUri =
      this.locationType === "local"
        ? vscode.Uri.parse(this.tarFileLocation)
        : effectiveExternalApi?.getTarFileUri(this.tarFileLocation);

    if (!tarFileUri) return [];
    const variables = getVariablesFromUri(documentUri, false);
    if (this.searchPattern) {
      this.searchPattern = SettingsService.evaluateVariables(
        this.searchPattern,
        variables,
      );
    }
    return vscode.workspace.fs
      .readDirectory(
        vscode.Uri.parse(
          `${TarCopybookFileSystemProvider.SCHEME}://${tarFileUri.fsPath}?searchPath=${this.searchPattern}#${dialect}`,
        ),
      )
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
    const allFiles = await vscode.workspace.fs
      .readDirectory(
        vscode.Uri.parse(
          `${TarCopybookFileSystemProvider.SCHEME}://${tarFileUri.fsPath}?searchPath=${this.searchPattern}#${dialect}`,
        ),
      )
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
