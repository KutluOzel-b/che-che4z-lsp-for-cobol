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

import { DATASET, TAR_FOLDER } from "../../constants";
import * as vscode from "vscode";
import CopybookLib from "./CopybookLib";
import { LibDefinition } from "../ProcessorGroupsLoader";
import { externalApis } from "../ExternalAPIsService";
import { ZoweLib } from "./ZoweLib";
import { extractTarPath, isTarPath } from "../util/Utils";
import { TarUtil } from "../util/TarUtil";

export class DatasetLib extends ZoweLib implements CopybookLib {
  private internalPath: string | undefined;
  private isTar: boolean = false;
  constructor(
    private dsn: string,
    profile?: string,
  ) {
    super(profile);
    if (isTarPath(dsn)) {
      ({ tarPath: this.dsn, internalPath: this.internalPath } =
        extractTarPath(dsn));
      this.isTar = true;
    }
  }

  static create(config: LibDefinition) {
    if (typeof config === "object" && DATASET in config) {
      return new DatasetLib(config.dataset, config.profile);
    }
  }

  async accessCheck(profile: string): Promise<void> {
    await vscode.workspace.fs.stat(
      vscode.Uri.parse(`zowe-ds:/${profile}/${this.dsn}?fetch=true`),
    );
  }

  async resolveCopybookUri(
    copybookName: string,
    documentUri: vscode.Uri,
    _dialect: string,
  ): Promise<vscode.Uri | undefined> {
    const profile = this.getProfile(documentUri);

    if (!(await this.configCheck(documentUri))) {
      return;
    }
    if (this.isTar) {
      if (
        !(await externalApis?.isPresentLocally(`${TAR_FOLDER}/${this.dsn}`))
      ) {
        await externalApis.dsnService?.downloadFile(this.dsn, profile);
      }
      if (externalApis.dsnService) {
        return await TarUtil.resolveTarFile(
          documentUri,
          _dialect,
          copybookName,
          externalApis,
          {
            tarPath: this.dsn,
            internalPath: this.internalPath,
          },
        );
      }
      return;
    }

    const member = await externalApis.dsnService?.hasMember(
      profile,
      this.dsn,
      copybookName,
      [],
    );

    if (member) {
      return vscode.Uri.parse(
        `zowe-ds:/${profile}/${this.dsn}/${member.name}${member.extension ? member.extension : ""}`,
      );
    }
  }

  async listCopybooks(
    documentUri: vscode.Uri,
    _dialect: string,
  ): Promise<string[]> {
    const profile = this.getProfile(documentUri);

    if (!(await this.configCheck(documentUri))) {
      return [];
    }

    const members = await externalApis.dsnService?.getAllMembers(
      profile,
      this.dsn,
    );

    return members?.map((m) => m.name) ?? [];
  }
}
