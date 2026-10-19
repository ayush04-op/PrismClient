import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Animated dark mesh gradient (purple / midnight blue / teal blobs).
class MeshBackground extends StatefulWidget {
  const MeshBackground({super.key});

  @override
  State<MeshBackground> createState() => _MeshBackgroundState();
}

class _MeshBackgroundState extends State<MeshBackground> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 24),
  )..repeat();

  static const _blobs = <(Color, double, double)>[
    (Color(0xFF5B2EFF), 0.0, 0.9),
    (Color(0xFF0B1E6B), 2.1, 1.1),
    (Color(0xFF0A7C7C), 4.2, 0.8),
    (Color(0xFF8A2BE2), 5.4, 1.0),
  ];

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) {
          final t = _c.value * 2 * math.pi;
          return Stack(
            fit: StackFit.expand,
            children: [
              const ColoredBox(color: Color(0xFF05060F)),
              for (final (color, phase, speed) in _blobs)
                Align(
                  alignment: Alignment(
                    0.8 * math.sin(t * speed + phase),
                    0.8 * math.cos(t * speed * 0.8 + phase * 1.3),
                  ),
                  child: Container(
                    width: 380,
                    height: 380,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [color.withOpacity(0.75), color.withOpacity(0)],
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}
