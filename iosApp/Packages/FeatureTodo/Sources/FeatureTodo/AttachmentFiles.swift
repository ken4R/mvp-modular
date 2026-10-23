import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

/// Préparation des fichiers choisis avant de les passer à KMP (ADR 0014), et lecture pour l'affichage.
enum AttachmentFiles {
    /// Plus grand côté d'une photo jointe, en pixels.
    static let maxPhotoSide = 2048
    /// Taille au-delà de laquelle un fichier n'est même pas lu (KMP refuse au-delà de 10 Mo).
    static let maxFileBytes = 10 * 1024 * 1024

    /// Photo réenregistrée en JPEG, 2 048 px au plus, orientation appliquée : sans métadonnées (position GPS,
    /// appareil, date). HEIC et PNG deviennent du JPEG, seul format photo accepté par KMP.
    static func jpeg(from data: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary) else {
            return nil
        }
        return downsampled(source, maxPixelSize: maxPhotoSide).flatMap { UIImage(cgImage: $0).jpegData(compressionQuality: 0.85) }
    }

    /// Miniature d'une photo jointe, sans décoder l'image entière.
    static func thumbnail(path: String, maxPixelSize: Int) -> UIImage? {
        let url = URL(fileURLWithPath: path) as CFURL
        guard let source = CGImageSourceCreateWithURL(url, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        return downsampled(source, maxPixelSize: maxPixelSize).map { UIImage(cgImage: $0) }
    }

    /// Début d'un fichier texte joint (200 Ko au plus), pour l'aperçu.
    static func textPreview(path: String) -> String? {
        guard let handle = FileHandle(forReadingAtPath: path) else { return nil }
        defer { try? handle.close() }
        let data = (try? handle.read(upToCount: 200 * 1024)) ?? Data()
        return String(decoding: data, as: UTF8.self)
    }

    /// Fichier choisi dans « Fichiers » : nom, type MIME et contenu, prêt pour `NewTodoAttachment`.
    /// `nil` si le fichier est illisible, trop gros ou si une image ne se décode pas.
    static func read(url: URL) -> (fileName: String, mimeType: String, data: Data)? {
        // URL hors du bac à sable de l'app : accès accordé le temps de la lecture.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentTypeKey])
        if let size = values?.fileSize, size > maxFileBytes { return nil }
        guard let data = try? Data(contentsOf: url, options: .mappedIfSafe), data.count <= maxFileBytes else { return nil }
        let type = values?.contentType ?? UTType(filenameExtension: url.pathExtension)
        if type?.conforms(to: .image) == true {
            guard let jpeg = jpeg(from: data) else { return nil }
            return (url.deletingPathExtension().lastPathComponent + ".jpg", "image/jpeg", jpeg)
        }
        let mimeType: String
        if type?.conforms(to: .pdf) == true {
            mimeType = "application/pdf"
        } else if type?.conforms(to: .plainText) == true {
            mimeType = "text/plain"
        } else {
            mimeType = type?.preferredMIMEType ?? "application/octet-stream"
        }
        return (url.lastPathComponent, mimeType, data)
    }

    private static func downsampled(_ source: CGImageSource, maxPixelSize: Int) -> CGImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }
}

/// Taille lisible : « 850 Ko », « 2,4 Mo ».
func formattedSize(_ bytes: Int64) -> String {
    let formatter = ByteCountFormatter()
    formatter.countStyle = .binary
    formatter.allowedUnits = [.useKB, .useMB]
    return formatter.string(fromByteCount: bytes)
}
