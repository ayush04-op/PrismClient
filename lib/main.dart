import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'core/routing/app_router.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env');
  await LiquidGlassWidgets.initialize(); // pre-warm shaders

  runApp(
    ProviderScope(
      child: LiquidGlassWidgets.wrap(
        child: const GlassGramApp(),
        brightnessResolver: (_) => Brightness.dark,
      ),
    ),
  );
}

class GlassGramApp extends ConsumerWidget {
  const GlassGramApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'GlassGram',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      scrollBehavior: const _SmoothScroll(),
      darkTheme: ThemeData(
        fontFamily: 'Inter',
        brightness: Brightness.dark,
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6C4DFF),
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: Colors.transparent,
      ),
      routerConfig: ref.watch(routerProvider),
    );
  }
}

/// iOS-style bouncing physics, drag with any pointer.
class _SmoothScroll extends MaterialScrollBehavior {
  const _SmoothScroll();

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) =>
      const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics());

  @override
  Widget buildOverscrollIndicator(BuildContext context, Widget child, ScrollableDetails details) => child;
}
