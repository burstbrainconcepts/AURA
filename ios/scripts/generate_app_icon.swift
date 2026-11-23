import AppKit
import CoreGraphics
import Foundation

let output = CommandLine.arguments.dropFirst().first ?? "ios/AURA/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
let size = 1024
let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: size,
    pixelsHigh: size,
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
)!
NSGraphicsContext.saveGraphicsState()
let graphics = NSGraphicsContext(bitmapImageRep: bitmap)!
NSGraphicsContext.current = graphics
let ctx = graphics.cgContext
ctx.setAllowsAntialiasing(true)
ctx.setShouldAntialias(true)

let bounds = CGRect(x: 0, y: 0, width: size, height: size)
let background = NSBezierPath(roundedRect: bounds, xRadius: 224, yRadius: 224)
NSGradient(colors: [NSColor(calibratedRed: 0.067, green: 0.09, blue: 0.21, alpha: 1), NSColor(calibratedRed: 0.02, green: 0.03, blue: 0.09, alpha: 1)])!.draw(in: background, angle: 45)

ctx.setStrokeColor(NSColor(calibratedRed: 0.33, green: 0.25, blue: 0.79, alpha: 0.28).cgColor)
ctx.setLineWidth(5)
ctx.strokeEllipse(in: CGRect(x: 222, y: 222, width: 580, height: 580))

if let glow = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [
    NSColor(calibratedRed: 0.53, green: 0.22, blue: 1, alpha: 0.35).cgColor,
    NSColor(calibratedRed: 0.53, green: 0.22, blue: 1, alpha: 0).cgColor
] as CFArray, locations: [0, 1]) {
    ctx.drawRadialGradient(glow, startCenter: CGPoint(x: 512, y: 512), startRadius: 10, endCenter: CGPoint(x: 512, y: 512), endRadius: 210, options: [])
}

func gradientStroke(_ path: CGPath, width: CGFloat, colors: [NSColor], start: CGPoint, end: CGPoint) {
    guard let stroked = path.copy(strokingWithWidth: width, lineCap: .round, lineJoin: .round, miterLimit: 10) else { return }
    ctx.saveGState()
    ctx.addPath(stroked)
    ctx.clip()
    let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map { $0.cgColor } as CFArray, locations: [0, 0.45, 1])!
    ctx.drawLinearGradient(gradient, start: start, end: end, options: [])
    ctx.restoreGState()
}

let violet = CGMutablePath()
violet.move(to: CGPoint(x: 198, y: 408))
violet.addCurve(to: CGPoint(x: 520, y: 819), control1: CGPoint(x: 300, y: 644), control2: CGPoint(x: 406, y: 819))
violet.addCurve(to: CGPoint(x: 816, y: 320), control1: CGPoint(x: 650, y: 819), control2: CGPoint(x: 719, y: 600))
violet.addCurve(to: CGPoint(x: 555, y: 538), control1: CGPoint(x: 733, y: 399), control2: CGPoint(x: 651, y: 496))
violet.addCurve(to: CGPoint(x: 198, y: 408), control1: CGPoint(x: 427, y: 593), control2: CGPoint(x: 302, y: 503))
gradientStroke(violet, width: 54, colors: [
    NSColor(calibratedRed: 0.96, green: 0.87, blue: 1, alpha: 1),
    NSColor(calibratedRed: 0.65, green: 0.30, blue: 1, alpha: 1),
    NSColor(calibratedRed: 0.34, green: 0.15, blue: 0.85, alpha: 1)
], start: CGPoint(x: 240, y: 800), end: CGPoint(x: 820, y: 200))

let cyan = CGMutablePath()
cyan.move(to: CGPoint(x: 190, y: 421))
cyan.addCurve(to: CGPoint(x: 807, y: 430), control1: CGPoint(x: 340, y: 269), control2: CGPoint(x: 590, y: 237))
cyan.addCurve(to: CGPoint(x: 764, y: 581), control1: CGPoint(x: 862, y: 479), control2: CGPoint(x: 839, y: 566))
cyan.addCurve(to: CGPoint(x: 370, y: 373), control1: CGPoint(x: 657, y: 603), control2: CGPoint(x: 516, y: 489))
gradientStroke(cyan, width: 48, colors: [
    NSColor(calibratedRed: 0.73, green: 1, blue: 1, alpha: 1),
    NSColor(calibratedRed: 0.09, green: 0.90, blue: 0.95, alpha: 1),
    NSColor(calibratedRed: 0.03, green: 0.49, blue: 0.80, alpha: 1)
], start: CGPoint(x: 180, y: 700), end: CGPoint(x: 850, y: 350))

ctx.setFillColor(NSColor(calibratedRed: 0.91, green: 0.84, blue: 1, alpha: 1).cgColor)
ctx.fillEllipse(in: CGRect(x: 476, y: 476, width: 72, height: 72))
ctx.setStrokeColor(NSColor(calibratedRed: 0.91, green: 0.84, blue: 1, alpha: 0.7).cgColor)
ctx.setLineWidth(3)
ctx.strokeEllipse(in: CGRect(x: 454, y: 454, width: 116, height: 116))

NSGraphicsContext.restoreGraphicsState()
let directory = URL(fileURLWithPath: output).deletingLastPathComponent()
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
let png = bitmap.representation(using: .png, properties: [:])!
try png.write(to: URL(fileURLWithPath: output))
print("Generated AURA AppIcon: \(output)")
