import * as vscode from "vscode";
import * as tar from "tar-stream";
import { Readable } from "stream";

export class TarUtil {
  public static ebcdicToAsciiArray(input: Buffer): number[] {
    return [...input].map((it) => this.EBCDIC_TO_ASCII[it]);
  }

  public static async readTarFile(
    tarFilePath: vscode.Uri,
    emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>,
  ) {
    const result: {
      fileName: string;
      fileData: {
        fileContent: Uint8Array<ArrayBuffer> | string[];
        fileMetadata: vscode.FileStat;
      };
    }[] = [];
    let currentDirFiles: string[] = [];
    let currentDirName: string;
    let dirMetaData: vscode.FileStat;
    return new Promise<
      {
        fileName: string;
        fileData: {
          fileContent: Uint8Array<ArrayBuffer> | string[];
          fileMetadata: vscode.FileStat;
        };
      }[]
    >((resolve, reject) => {
      let isEbcidic: boolean | undefined;
      const extract = tar.extract();
      extract.on("entry", (header, stream, next) => {
        const virtualPath = vscode.Uri.joinPath(tarFilePath, header.name);
        if (header.type === "file") {
          emitter.fire([
            { type: vscode.FileChangeType.Created, uri: virtualPath },
          ]);
          stream.on("data", (chunk: Buffer) => {
            currentDirFiles.push(header.name);
            const fileName = header.name;
            const fileMetadata: vscode.FileStat =
              this.getFileMetadataFromHeader(header);
            // autodetection by counting the number of 0x40 and 0x20
            // if you have more 0x40 it is EBCDIC and if the latter is ASCII
            if (isEbcidic === undefined) {
              isEbcidic =
                chunk.filter((e) => e === 64).length >
                chunk.filter((e) => e === 32).length;
            }
            let fileBuffer;
            if (isEbcidic) {
              fileBuffer = this.ebcdicToAsciiArray(chunk);
            } else {
              fileBuffer = chunk;
            }
            const fileContent = new Uint8Array(fileBuffer);
            result.push({ fileName, fileData: { fileContent, fileMetadata } });
            // update the cache with tar location + internal path + file content
          });

          stream.on("end", () => {
            next();
          });

          stream.on("error", (err) => {
            console.error(`tar stream error for ${header.name}:`, err);
            next(err);
          });
        } else if (header.type === "directory") {
          emitter.fire([
            { type: vscode.FileChangeType.Created, uri: virtualPath },
          ]);
          result.push({
            fileName: currentDirName,
            fileData: {
              fileContent: currentDirFiles,
              fileMetadata: dirMetaData,
            },
          });
          currentDirFiles = [];
          currentDirName = header.name;
          dirMetaData = this.getFileMetadataFromHeader(header);
        } else {
          next();
        }
      });

      extract.on("finish", () => {
        console.log("---reading archive complete ---");
        resolve(result);
      });

      extract.on("error", (err: Error) => {
        reject(err);
        console.log("---error on reading archive ---");
      });

      vscode.workspace.fs.readFile(tarFilePath).then((arr) => {
        const bufferStream = new Readable();
        bufferStream.push(Buffer.from(arr));
        bufferStream.push(null);
        bufferStream.pipe(extract);
      });
    });
  }
  static getFileMetadataFromHeader(header: tar.Headers) {
    return {
      mtime: header.mtime?.getTime() || 0,
      ctime: 0,
      size: header.size || 0,
      name: header.name,
      type: this.getFileTypeFromtarHeader(header.type),
    };
  }
  static getFileTypeFromtarHeader(type: string | null | undefined) {
    if (type === "file") return vscode.FileType.File;
    if (type === "directory") return vscode.FileType.Directory;
    if (type === "link" || type === "symlink")
      return vscode.FileType.SymbolicLink;
    return vscode.FileType.Unknown;
  }

  // prettier-ignore
  public static EBCDIC_TO_ASCII = [
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
}
