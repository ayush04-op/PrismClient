import 'package:flutter/material.dart';

/// Dark mesh gradient (purple / midnight blue / teal blobs).
///
/// Deliberately static: an animated background forces every glass blur on the
/// screen to re-sample every frame, which is the main source of lag.
class MeshBackground extends StatelessWidget {
  const MeshBackground({super.key});

  static const _blobs = <(Color, Alignment, double)>[
    (Color(0xFF5B2EFF), Alignment(-0.7, -0.6), 460),
    (Color(0xFF0B1E6B), Alignment(0.9, -0.1), 520),
    (Color(0xFF0A7C7C), Alignment(-0.4, 0.9), 480),
    (Color(0xFF8A2BE2), Alignment(0.8, 0.8), 420),
  ];

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Stack(
        fit: StackFit.expand,
        children: [
          const ColoredBox(color: Color(0xFF05060F)),
          for (final (color, align, size) in _blobs)
            Align(
              alignment: align,
              child: Container(
                width: size,
                height: size,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [color.withOpacity(0.7), color.withOpacity(0)],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
