import { Uri } from "vscode";
import CopybookLib from "./CopybookLib";
import * as vscode from "vscode";
import { getProfileNameForCopybook } from "../util/ProfileUtils";
import { externalApis } from "../ExternalAPIsService";
import { TarUtil } from "../util/TarUtil";
import { LibDefinition } from "../ProcessorGroupsLoader";

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
    const isUnderExtStorage = this.locationType === "local" ? false : true;
    if (
      !(await externalApis?.isPresentLocally(tarFileUri, isUnderExtStorage)) &&
      canDownloadTar
    ) {
      await externalApis.dsnService?.downloadFile(
        this.tarFileLocation,
        profile,
      );
    }
    if (
      tarFileUri &&
      (await externalApis?.isPresentLocally(tarFileUri, isUnderExtStorage))
    ) {
      return await TarUtil.resolveTarFile(
        documentUri,
        dialect,
        copybookName,
        externalApis,
        {
          tarName: this.tarFileLocation,
          internalPath: this.searchPattern,
          tarFileUri: tarFileUri,
        },
      );
    }
  }
  listCopybooks(documentUri: Uri, dialect: string): Promise<string[]> {
    throw new Error("Method not implemented.");
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
      return new TarCopybookLib(
        config["locationType"],
        config["tarFileLocation"],
        config["searchPattern"],
        config["profile"],
      );
    }
  }
}
