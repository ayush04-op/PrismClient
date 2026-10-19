import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../features/auth/auth_controller.dart';
import '../../features/auth/auth_screen.dart';
import '../theme/mesh_background.dart';

/// Bridges Riverpod auth state to GoRouter's refresh mechanism.
class _AuthRefresh extends ChangeNotifier {
  _AuthRefresh(Ref ref) {
    ref.listen<AuthState>(authProvider, (_, __) => notifyListeners());
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _AuthRefresh(ref);
  return GoRouter(
    initialLocation: '/auth',
    refreshListenable: refresh,
    redirect: (context, state) {
      final step = ref.read(authProvider).step;
      final onAuth = state.matchedLocation == '/auth';
      if (step == AuthStep.ready) return onAuth ? '/' : null;
      return onAuth ? null : '/auth';
    },
    routes: [
      GoRoute(path: '/auth', builder: (_, __) => const AuthScreen()),
      GoRoute(path: '/', builder: (_, __) => const _ChatsPlaceholder()),
    ],
  );
});

/// Replaced by the real chat list in Phase 3.
class _ChatsPlaceholder extends StatelessWidget {
  const _ChatsPlaceholder();

  @override
  Widget build(BuildContext context) {
    return GlassScaffold(
      background: const MeshBackground(),
      appBar: const GlassAppBar(title: Text('Chats')),
      body: const Center(child: Text('Logged in. Chat list arrives in Phase 3.')),
    );
  }
}
