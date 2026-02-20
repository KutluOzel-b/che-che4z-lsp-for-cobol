import * as vscode from "vscode";
import * as tar from "tar-stream";
import { Readable, Stream } from "stream";
import { SEPARATOR } from "../../provider/TarCopybookFileSystemProvider";
import { sep } from "path";
import { Memoize } from "./Memoize";

export type refBool = { value: boolean | undefined };

export async function readTarFile(
  tarFileUri: vscode.Uri,
  emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
) {
  return new Promise<TarContent[]>((resolve, reject) => {
    const result: TarContent[] = [];
    const links: { name: string; linkname: string }[] = [];
    const extract = tar.extract();
    const ebcdicRef: refBool = { value: undefined };
    const rootHeader: tar.Headers = { name: "/" };
    handleDirectory(
      result,
      rootHeader,
      emitter,
      tarFileUri,
      () => (_error?: unknown) => {
        return;
      },
    );
    extract.on("entry", (header, stream, next) => {
      if (header.type === "file") {
        handleFile(
          result,
          stream,
          header,
          emitter,
          ebcdicRef,
          tarFileUri,
          next,
        );
      } else if (header.type === "directory") {
        handleDirectory(result, header, emitter, tarFileUri, next);
      } else if (header.type == "symlink" || header.type == "link") {
        handleLink(links, header, next);
      } else {
        next();
      }
    });

    extract.on("finish", () => {
      linksToActuals(result, links);
      console.log("---reading archive complete ---");
      resolve(result);
    });

    extract.on("error", (err: Error) => {
      console.log("---error on reading archive ---");
      reject(err);
    });

    vscode.workspace.fs.readFile(tarFileUri).then(
      (arr) => {
        const bufferStream = new Readable();
        bufferStream.push(Buffer.from(arr));
        bufferStream.push(null);
        bufferStream.pipe(extract);
      },
      (rej: Error) => {
        reject(rej);
        throw rej;
      },
    );
  });
}

// prettier-ignore
export const EBCDIC_TO_ASCII = [
  //         0x_0  0x_1  0x_2  0x_3  0x_4  0x_5  0x_6  0x_7  0x_8  0x_9  0x_a  0x_b  0x_c  0x_d  0x_e  0x_f
  /* 0x0_ */ 0x00, 0x01, 0x02, 0x03, 0x3f, 0x09, 0x3f, 0x7f, 0x3f, 0x3f, 0x3f, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 
  /* 0x1_ */ 0x10, 0x11, 0x12, 0x13, 0x3f, 0x0a, 0x08, 0x3f, 0x18, 0x19, 0x3f, 0x3f, 0x1c, 0x1d, 0x1e, 0x1f, 
  /* 0x2_ */ 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x17, 0x1b, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x05, 0x06, 0x07,
  /* 0x3_ */ 0x3f, 0x3f, 0x16, 0x3f, 0x3f, 0x3f, 0x3f, 0x04, 0x3f, 0x3f, 0x3f, 0x3f, 0x14, 0x15, 0x3f, 0x1a, 
  /* 0x4_ */ 0x20, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x2e, 0x3c, 0x28, 0x2b, 0x7c, 
  /* 0x5_ */ 0x26, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x21, 0x24, 0x2a, 0x29, 0x3b, 0x5e, 
  /* 0x6_ */ 0x2d, 0x2f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x2c, 0x25, 0x5f, 0x3e, 0x3f, 
  /* 0x7_ */ 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x60, 0x3a, 0x23, 0x40, 0x27, 0x3d, 0x22,
  /* 0x8_ */ 0x3f, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 
  /* 0x9_ */ 0x3f, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 
  /* 0xa_ */ 0x3f, 0x7e, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x3f, 0x3f, 0x3f, 0x5b, 0x3f, 0x3f, 
  /* 0xb_ */ 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x5d, 0x3f, 0x3f, 
  /* 0xc_ */ 0x7b, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f,
  /* 0xd_ */ 0x7d, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 
  /* 0xe_ */ 0x5c, 0x3f, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 
  /* 0xf_ */ 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f,
];

export type TarContent = {
  fileName: string;
  fileData: {
    fileContent: Uint8Array<ArrayBuffer> | string[] | undefined;
    fileMetadata: vscode.FileStat;
  };
};
export function splitTarfileUri(tarfileUri: vscode.Uri) {
  const str = tarfileUri.path.split(SEPARATOR);
  return {
    tarfilePath: str[1] ? str[0].slice(0, -1) : str[0],
    directory: str[1] == undefined ? sep : str[1],
  };
}
export function getDirectories(filePath: string): string[] {
  const parts = filePath.split("/");
  const dirs: string[] = [];
  for (let i = 1; i < parts.length; i++) {
    dirs.push(vscode.Uri.file(parts.slice(0, i).join("/")).path.concat("/"));
  }
  return dirs;
}
export function getFilePaths(header: tar.Headers, tarfileUri: vscode.Uri) {
  const fileName = vscode.Uri.file(header.name).path;
  const virtualPath = vscode.Uri.joinPath(tarfileUri, SEPARATOR, fileName);
  return { fileName, virtualPath };
}
export function fireEvent(
  emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
  type: vscode.FileChangeType,
  uri: vscode.Uri,
) {
  emitter.fire([{ type, uri }]);
}
// autodetection by counting the number of 0x40 and 0x20
// if you have more 0x40 it is EBCDIC and if the latter is ASCII
export function isEbcdic(chunk: Buffer) {
  return (
    chunk.reduce((acc, e) => acc + (e === 64 ? 1 : 0), 0) >
    chunk.reduce((acc, e) => acc + (e === 32 ? 1 : 0), 0)
  );
}
export function getFileMetadataFromHeader(header: tar.Headers) {
  return {
    mtime: header.mtime?.getTime() || 0,
    ctime: 0,
    size: header.size || 0,
    name: header.name,
    type: getFileTypeFromtarHeader(header.type),
  };
}
export function getFileTypeFromtarHeader(type: string | null | undefined) {
  if (type === "file") return vscode.FileType.File;
  if (type === "directory") return vscode.FileType.Directory;
  if (type === "link" || type === "symlink")
    return vscode.FileType.SymbolicLink;
  return vscode.FileType.Unknown;
}
export function ebcdicToAsciiArray(input: Buffer): number[] {
  return [...input].map((it) => EBCDIC_TO_ASCII[it]);
}
export function getTarCached(
  emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
) {
  return new Memoize(
    async (tarFileUri: vscode.Uri) => {
      return await readTarFile(tarFileUri, emitter);
    },
    undefined,
    (tarFileUri: vscode.Uri) => {
      return tarFileUri.fsPath;
    },
  );
}
function handleFile(
  result: TarContent[],
  stream: Stream.PassThrough,
  header: tar.Headers,
  emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
  ebcdicRef: refBool,
  tarFileUri: vscode.Uri,
  next: (error?: unknown) => void,
) {
  const { fileName, virtualPath } = getFilePaths(header, tarFileUri);
  const fileMetadata: vscode.FileStat = getFileMetadataFromHeader(header);
  const directories: Set<string> = new Set<string>();

  getDirectories(header.name).forEach((x) => directories.add(x));
  fireEvent(emitter, vscode.FileChangeType.Created, virtualPath);
  stream.on("data", (chunk: Buffer) => {
    if (ebcdicRef.value === undefined) {
      ebcdicRef.value = isEbcdic(chunk);
    }
    let fileBuffer;
    if (ebcdicRef.value) {
      fileBuffer = ebcdicToAsciiArray(chunk);
    } else {
      fileBuffer = chunk;
    }
    const fileContent = new Uint8Array(fileBuffer);
    result.push({
      fileName,
      fileData: { fileContent, fileMetadata },
    });
    directories.forEach((x) => {
      if (!result.find((res) => res.fileName == x)) {
        const newContent: TarContent = {
          fileName: x,
          fileData: {
            fileContent: undefined,
            fileMetadata: {
              ctime: fileMetadata.ctime,
              mtime: fileMetadata.mtime,
              type: vscode.FileType.Directory,
              size: 0,
            },
          },
        };
        result.push(newContent);
        fireEvent(
          emitter,
          vscode.FileChangeType.Created,
          vscode.Uri.joinPath(tarFileUri, newContent.fileName),
        );
      }
    });
  });

  stream.on("end", () => {
    next();
  });

  stream.on("error", (err) => {
    console.error(`tar stream error for ${header.name}:`, err);
    next(err);
  });
}
function handleDirectory(
  result: TarContent[],
  header: tar.Headers,
  emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
  tarFileUri: vscode.Uri,
  next: (error?: unknown) => void,
) {
  const { fileName, virtualPath } = getFilePaths(header, tarFileUri);
  const fileMetadata: vscode.FileStat = getFileMetadataFromHeader(header);
  if (!result.find((res) => res.fileName == fileName)) {
    const directory = vscode.Uri.file(fileName).path;
    result.push({
      fileName: directory,
      fileData: { fileContent: undefined, fileMetadata },
    });
  }
  fireEvent(emitter, vscode.FileChangeType.Created, virtualPath);
  next();
}
function handleLink(
  links: { name: string; linkname: string }[],
  header: tar.Headers,
  next: (error?: unknown) => void,
) {
  if (header.linkname)
    links.push({ linkname: header.linkname, name: header.name });
  next();
}
function linksToActuals(
  result: TarContent[],
  links: { name: string; linkname: string }[],
) {
  links.forEach((link) => {
    const match = result.find(
      (x) =>
        link.linkname.includes(x.fileName) &&
        x.fileData.fileMetadata.type != vscode.FileType.Directory,
    );
    if (match) {
      match.fileName = vscode.Uri.file(link.name).path;
      result.push(match);
    }
  });
}
