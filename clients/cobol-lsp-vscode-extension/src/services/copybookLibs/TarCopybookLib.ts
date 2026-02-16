import { Uri } from "vscode";
import CopybookLib from "./CopybookLib";
import * as vscode from "vscode";
import { getProfileNameForCopybook } from "../util/ProfileUtils";
import { externalApis } from "../ExternalAPIsService";
import { LibDefinition } from "../ProcessorGroupsLoader";
import {
  SEPARATOR,
  TarCopybookFileSystemProvider,
} from "../../provider/TarCopybookFileSystemProvider";
import { SettingsService } from "../Settings";
import { getVariablesFromUri } from "../util/FSUtils";
import { Minimatch } from "minimatch";
import { TarContent } from "../util/TarUtil";
import { Memoize } from "../util/Memoize";

export class TarCopybookLib implements CopybookLib {
  private matcher: Minimatch;
  constructor(
    private locationType: "DSN" | "USS" | "local",
    private tarFileLocation: string,
    private searchPattern: string,
    private tarCache?: Memoize<[tarFileUri: vscode.Uri], TarContent[]>,
    private profile?: string,
  ) {
    this.matcher = new Minimatch(vscode.Uri.file(this.searchPattern).path, {
      nocase: true,
      dot: true,
    });
  }

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
      if (!tarFileUri) return;
    } else {
      const binaryDownloader = externalApis.binaryDownloader;
      if (!binaryDownloader) return;

      tarFileUri = binaryDownloader.getTarFileUri(evaluatedTarPath);
      const isAvailableAlready =
        await binaryDownloader.isPresentLocally(tarFileUri);

      if (!isAvailableAlready) {
        const profile = this.getProfile(documentUri);
        if (this.locationType === "DSN") {
          const members = await externalApis.dsnService?.getAllMembers(
            profile,
            this.tarFileLocation,
          );
          if (!members || !(members.length > 0)) return;
        } else {
          const members = await externalApis.ussService?.getAllMembers(
            profile,
            this.tarFileLocation,
            [".tar", ".cobol-ls-tar", ""],
          );
          if (!members || !(members.length > 0)) return;
        }
        if (
          !(await binaryDownloader.downloadFile(
            evaluatedTarPath,
            profile,
            this.locationType,
          ))
        )
          return;
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
    if (matchingFilePath) {
      return matchingFilePath;
    }
  }

  async listCopybooks(documentUri: Uri, _dialect: string): Promise<string[]> {
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
      if (!tarFileUri) return [];
    } else {
      const binaryDownloader = externalApis.binaryDownloader;
      if (!binaryDownloader) return [];

      tarFileUri = binaryDownloader.getTarFileUri(evaluatedTarPath);
      const isAvailableAlready =
        await binaryDownloader.isPresentLocally(tarFileUri);

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

    const allFiles = await this.tarCache?.execute(tarFileUri);
    if (!allFiles || allFiles.length < 1) return [];
    const matchingFiles = allFiles.filter((e) => {
      return this.matcher.match(e.fileName);
    });
    return matchingFiles.map((x) => this.getFilenameFromPath(x.fileName));
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
          config["searchPattern"] ?? "**",
          externalApis.tarCache,
          config["profile"],
        );
      return new TarCopybookLib(
        config["locationType"],
        config["tarFileLocation"],
        config["searchPattern"] ?? "**",
        externalApis.tarCache,
      );
    }
  }

  private async findMatchingFile(
    tarFileUri: Uri,
    dialect: string,
    documentUri: Uri,
    copybookName: string,
  ) {
    const allFiles = await this.tarCache?.execute(tarFileUri);
    if (!allFiles || allFiles.length < 1) return;

    const matchingFiles = allFiles.filter((e) => {
      return this.matcher.match(e.fileName);
    });
    const allowedExtensions = await SettingsService.getCopybookExtension(
      documentUri,
      dialect,
    );
    const probableCopybooks = new Set(
      allowedExtensions
        .map((e) => e.toUpperCase())
        .map((ext) => copybookName.toUpperCase() + ext),
    );
    const matchingFilePath = matchingFiles.find((file) => {
      const filename = this.getFilenameFromPath(file.fileName, true);
      if (probableCopybooks.has(filename.toUpperCase())) {
        return true;
      }
      return false;
    });
    if (!matchingFilePath) return;
    return vscode.Uri.from({
      path: vscode.Uri.joinPath(
        tarFileUri,
        SEPARATOR,
        matchingFilePath.fileName,
      ).path,
      scheme: TarCopybookFileSystemProvider.SCHEME,
    });
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
