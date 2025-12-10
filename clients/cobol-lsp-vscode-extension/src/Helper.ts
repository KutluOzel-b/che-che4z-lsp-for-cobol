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
import * as vscode from "vscode";
import * as tar from "tar-stream";
import * as fs from "fs";
export interface SelectionObject {
  selection: vscode.Range;
  text: string;
}

export function extractTarToContent(tarFileName: string) {
  return new Promise<unknown[]>((resolve, reject) => {
    const extract = tar.extract();
    const result: unknown[] = [];
    const testFileUri = vscode.Uri.joinPath(
      vscode.Uri.parse(__dirname),
      "__tests__",
      "resources",
      "tar",
      tarFileName,
    );
    const tarUri = vscode.Uri.file(testFileUri.fsPath);
    fs.createReadStream(tarUri.fsPath).pipe(extract);

    extract.on("entry", (header, stream, next) => {
      const chunks: Uint8Array<ArrayBuffer>[] = [];
      stream.on("data", (chunk: Uint8Array<ArrayBuffer>) => {
        chunks.push(chunk);
      });
      stream.on("end", () => {
        const buffer = Buffer.concat(chunks);
        result.push({
          fileName: header.name,
          fileData: {
            fileContent: buffer,
            fileMetadata: {
              mtime: header.mtime?.getTime?.() ?? Date.now(),
              ctime: 0,
              size: buffer.length,
              name: header.name,
              type: header.type === "file" ? 1 : 1,
            },
          },
        });
        next();
      });
      stream.on("error", reject);
    });
    extract.on("finish", () => {
      resolve(result);
    });
    extract.on("error", reject);
  });
}
