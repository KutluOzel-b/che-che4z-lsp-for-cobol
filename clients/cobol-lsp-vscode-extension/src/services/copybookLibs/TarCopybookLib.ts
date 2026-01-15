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
import { splitTarfileUri } from "../util/TarUtil";
import { outputChannel } from "../util/OutputChannel";

export class TarCopybookLib implements CopybookLib {
  private matcher: Minimatch;
  constructor(
    private locationType: "DSN" | "USS" | "local",
    private tarFileLocation: string,
    private searchPattern: string,
    private profile?: string,
  ) {
    this.matcher = new Minimatch(this.searchPattern, {
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

      try {
        await vscode.workspace.fs.stat(tarFileUri);
      } catch (_error) {
        outputChannel.appendLine(
          `Error on retrieving tar file ': ${tarFileUri.fsPath}' stats while trying to resolve copybook: '${copybookName}'`,
        );
        return;
      }
    } else {
      const effectiveExternalApi =
        this.locationType === "DSN"
          ? externalApis.dsnService
          : externalApis.ussService;

      tarFileUri = effectiveExternalApi?.getTarFileUri(evaluatedTarPath);
      const isAvailableAlready =
        await externalApis.isPresentLocally(tarFileUri);

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
      try {
        await vscode.workspace.fs.stat(tarFileUri);
      } catch (_error) {
        outputChannel.appendLine(
          `Error on retrieving tar file stats while listing copybooks: '${tarFileUri.fsPath}'`,
        );
        return [];
      }
    } else {
      const effectiveExternalApi =
        this.locationType === "DSN"
          ? externalApis.dsnService
          : externalApis.ussService;
      tarFileUri = effectiveExternalApi?.getTarFileUri(evaluatedTarPath);
      const isAvailableAlready =
        await externalApis.isPresentLocally(tarFileUri);

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
      path: tarFileUri.fsPath,
    });

    const allFiles = await this.readAllSubtree(directory);
    const matchingFiles = allFiles.filter((e) => {
      const { directory: directory } = splitTarfileUri(e);
      return this.matcher.match(directory);
    });
    return matchingFiles.map((x) => this.getFilenameFromPath(x.fsPath));
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
    });

    const allFiles = await this.readAllSubtree(directory);

    const matchingFiles = allFiles.filter((e) => {
      const { directory: directory } = splitTarfileUri(e);
      return this.matcher.match(directory);
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
      const filename = this.getFilenameFromPath(file.fsPath, true);
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

  private async readAllSubtree(uri: vscode.Uri): Promise<vscode.Uri[]> {
    const files: vscode.Uri[] = [];

    const entries = await vscode.workspace.fs.readDirectory(uri);

    for (const [name, type] of entries) {
      if (!uri.fsPath.includes(SEPARATOR))
        uri = vscode.Uri.joinPath(uri, SEPARATOR);
      const fullPath = vscode.Uri.joinPath(uri, name);

      if (type === vscode.FileType.File) {
        files.push(fullPath);
      } else if (type === vscode.FileType.Directory) {
        files.push(...(await this.readAllSubtree(fullPath)));
      }
    }

    return files;
  }
}
