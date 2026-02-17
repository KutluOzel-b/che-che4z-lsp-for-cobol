/*
 * Copyright (c) 2026 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Broadcom, Inc. - initial API and implementation
 */
import { TAR_FOLDER } from "../../../constants";
import { loadProfile } from "../../util/Utils";
import * as vscode from "vscode";

export class CopybookBinaryDownloader {
  constructor(
    private storagePath: vscode.Uri,
    private explorerAPI: IApiRegisterClient,
  ) {}
  public downloadFile(path: string, profile: string, type: "USS" | "DSN") {
    const loadedProfile = loadProfile(profile, this.explorerAPI);
    const tarUri = this.getTarFileUri(path);
    try {
      if (type == "DSN")
        return this.explorerAPI.getMvsApi(loadedProfile).getContents(path, {
          file: tarUri.fsPath,
          returnEtag: true,
          binary: true,
        });
      else
        return this.explorerAPI.getUssApi(loadedProfile).getContents(path, {
          file: tarUri.fsPath,
          returnEtag: true,
          binary: true,
        });
    } catch (_error) {
      return vscode.workspace.fs.delete(tarUri);
    }
  }

  public getTarFileUri(filePath: string) {
    return vscode.Uri.joinPath(this.storagePath, TAR_FOLDER, filePath);
  }
  public async isPresentLocally(
    inputPath: string | vscode.Uri | undefined,
    isUnderExtStorage: boolean = true,
  ) {
    if (!inputPath) return false;
    const uri =
      inputPath instanceof vscode.Uri
        ? inputPath
        : isUnderExtStorage
          ? vscode.Uri.joinPath(this.storagePath, inputPath)
          : vscode.Uri.parse(inputPath);
    try {
      await vscode.workspace.fs.stat(uri);
      return true;
    } catch (_error) {
      return false;
    }
  }
}
