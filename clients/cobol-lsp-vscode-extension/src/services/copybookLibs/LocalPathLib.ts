/*
 * Copyright (c) 2025 Broadcom.
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

import { externalApis } from "../ExternalAPIsService";
import { LocalFilesystemResourceService } from "../LocalFilesystemResourceService";
import { LibDefinition } from "../ProcessorGroupsLoader";
import { SettingsService } from "../Settings";
import { outputChannel } from "../util/OutputChannel";
import { TarUtil } from "../util/TarUtil";
import { extractTarPath, getUris, isTarPath } from "../util/Utils";
import CopybookLib from "./CopybookLib";
import * as vscode from "vscode";

export const localCopybooks = new LocalFilesystemResourceService();

export default class LocalPathLib implements CopybookLib {
  private internalPath: string | undefined;
  private isTar: boolean = false;
  constructor(private path: string) {
    if (isTarPath(path)) {
      ({ tarPath: this.path, internalPath: this.internalPath } =
        extractTarPath(path));
      this.isTar = true;
    }
  }

  static create(config: LibDefinition) {
    if (typeof config === "string") {
      return new LocalPathLib(config);
    }
  }

  async resolveCopybookUri(
    copybookName: string,
    documentUri: vscode.Uri,
    dialect: string,
  ) {
    const uris = getUris(documentUri, this.path);

    if (this.isTar) {
      if (await externalApis?.isPresentLocally(this.path, false)) {
        return await TarUtil.resolveTarFile(
          documentUri,
          dialect,
          copybookName,
          externalApis,
          {
            tarName: this.path,
            internalPath: this.internalPath,
            tarFileUri: vscode.Uri.parse(this.path),
          },
        );
      }
      return;
    }
    const allowedExtensions = await SettingsService.getCopybookExtension(
      documentUri,
      dialect,
    );

    // TODO: search in tar file locally and return if found or return undefined

    const promises = uris.map(async (uri) => {
      return await localCopybooks.searchDirectory(
        uri,
        copybookName,
        allowedExtensions ?? [],
      );
    });

    const results = await Promise.allSettled(promises);
    for (const result of results) {
      if (result.status === "fulfilled" && result.value) {
        return result.value;
      }
    }
  }

  async listCopybooks(
    documentUri: vscode.Uri,
    dialect: string,
  ): Promise<string[]> {
    const uris = getUris(documentUri, this.path);

    const allowedExtensions = await SettingsService.getCopybookExtension(
      documentUri,
      dialect,
    );

    const results = await Promise.allSettled(
      uris.map(async (directoryUri) =>
        localCopybooks.listDirectory(directoryUri, allowedExtensions ?? []),
      ),
    );

    const copybooks: string[] = [];

    results.forEach((result) => {
      if (result.status === "fulfilled") {
        result.value.forEach((copybook) => copybooks.push(copybook.filename));
      } else {
        outputChannel.error(
          `Unable to load copybooks completions: ${JSON.stringify(result.reason)}`,
        );
      }
    });

    return copybooks;
  }
}
