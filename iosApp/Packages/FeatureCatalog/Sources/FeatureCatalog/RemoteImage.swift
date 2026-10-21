import SwiftUI
import UIKit
import ImageIO
import DesignSystemUI

/// Images du catalogue : cache mémoire, et cache disque (URLCache dédié) relu en priorité,
/// pour qu'une image déjà vue reste visible hors ligne. Le dossier Caches peut être vidé par iOS si l'espace manque.
actor ImageLoader {
    static let shared = ImageLoader()

    /// Plus grand côté d'une image décodée, en pixels : suffisant pour la galerie plein écran.
    private static let maxPixelSize = 1200
    /// Au-delà, la réponse est refusée sans être décodée.
    private static let maxDownloadBytes = 10 * 1024 * 1024

    private let memory = NSCache<NSURL, UIImage>()
    private let disk: URLCache
    private let session: URLSession

    private init() {
        memory.totalCostLimit = 50 * 1024 * 1024
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("CatalogImages", isDirectory: true)
        disk = URLCache(memoryCapacity: 0, diskCapacity: 200 * 1024 * 1024, directory: directory)
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = disk
        session = URLSession(configuration: configuration)
    }

    /// Seules les URL HTTPS sont chargées (la logique KMP écarte déjà les autres).
    func image(for url: URL) async -> UIImage? {
        guard url.scheme?.lowercased() == "https" else { return nil }
        if let image = memory.object(forKey: url as NSURL) { return image }
        // Cache disque quel que soit son âge : une image de produit ne change pas.
        let request = URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad)
        if let cached = disk.cachedResponse(for: request), let image = Self.downsampled(cached.data) {
            remember(image, for: url)
            return image
        }
        guard let (data, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              data.count <= Self.maxDownloadBytes,
              let image = Self.downsampled(data)
        else { return nil }
        // Enregistré explicitement, même si le serveur ne donne pas d'en-têtes de cache.
        disk.storeCachedResponse(CachedURLResponse(response: response, data: data), for: request)
        remember(image, for: url)
        return image
    }

    private func remember(_ image: UIImage, for url: URL) {
        let cost = image.cgImage.map { $0.bytesPerRow * $0.height } ?? 0
        memory.setObject(image, forKey: url as NSURL, cost: cost)
    }

    /// Décode l'image réduite à `maxPixelSize` : une image énorme ne peut pas épuiser la mémoire.
    private static func downsampled(_ data: Data) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }
}

/// Image distante recadrée pour remplir son cadre (taille donnée par l'appelant), sur un fond neutre pendant le chargement.
struct RemoteImage: View {
    let url: String?
    @State private var image: UIImage?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Rectangle()
            .fill(Color(argb: DS.colors(colorScheme).surface))
            .overlay {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: DS.Radius.md))
            // Image décorative : le titre du produit est lu juste à côté.
            .accessibilityHidden(true)
            .task(id: url) {
                image = nil
                guard let url, let parsed = URL(string: url) else { return }
                image = await ImageLoader.shared.image(for: parsed)
            }
    }
}
