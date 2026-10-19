import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../core/tdlib/td_service.dart';
import '../../core/theme/mesh_background.dart';
import 'auth_controller.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key});

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _input = TextEditingController();
  AuthStep? _lastStep;

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);
    // The phone form shows immediately, even while TDLib is still starting.
    final step = auth.step == AuthStep.loading ? AuthStep.phone : auth.step;
    if (_lastStep != step) {
      _lastStep = step;
      _input.clear();
    }

    final (title, subtitle, label, keyboard, obscure) = switch (step) {
      AuthStep.code => ('Enter code', 'We sent a code to your Telegram app or SMS.', 'Code', TextInputType.number, false),
      AuthStep.password => (
          'Two-step verification',
          auth.hint?.isNotEmpty == true ? 'Hint: ${auth.hint}' : 'Enter your cloud password.',
          'Password',
          TextInputType.visiblePassword,
          true,
        ),
      _ => ('Your phone', 'Include country code, e.g. +91…', 'Phone number', TextInputType.phone, false),
    };

    return GlassScaffold(
      background: const MeshBackground(),
      body: Material(
        type: MaterialType.transparency,
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.all(24),
              child: GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(title, style: Theme.of(context).textTheme.headlineSmall, textAlign: TextAlign.center),
                      const SizedBox(height: 8),
                      Text(subtitle, style: const TextStyle(color: Colors.white70), textAlign: TextAlign.center),
                      const SizedBox(height: 24),
                      TextField(
                        controller: _input,
                        keyboardType: keyboard,
                        obscureText: obscure,
                        autofocus: true,
                        onSubmitted: (_) => _submit(step),
                        decoration: InputDecoration(
                          labelText: label,
                          filled: true,
                          fillColor: Colors.black.withOpacity(0.2),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(16)),
                        ),
                      ),
                      if (auth.error != null) ...[
                        const SizedBox(height: 12),
                        Text(auth.error!, style: const TextStyle(color: Colors.redAccent), textAlign: TextAlign.center),
                      ],
                      const SizedBox(height: 20),
                      // No spinners: an endlessly animating widget keeps the glass re-rendering.
                      FilledButton(
                        onPressed: auth.busy ? null : () => _submit(step),
                        child: Text(auth.busy ? 'Please wait…' : 'Continue'),
                      ),
                      if (auth.step == AuthStep.loading || auth.error != null) ...[
                        const SizedBox(height: 14),
                        ValueListenableBuilder<String>(
                          valueListenable: TdService.instance.debugLog,
                          builder: (_, v, __) => Text(
                            'Telegram: $v',
                            style: const TextStyle(fontSize: 11, color: Colors.white38),
                            textAlign: TextAlign.center,
                            maxLines: 4,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _submit(AuthStep step) {
    final v = _input.text;
    if (v.trim().isEmpty) return;
    final c = ref.read(authProvider.notifier);
    switch (step) {
      case AuthStep.phone:
        c.submitPhone(v);
      case AuthStep.code:
        c.submitCode(v);
      case AuthStep.password:
        c.submitPassword(v);
      default:
        break;
    }
  }
}
