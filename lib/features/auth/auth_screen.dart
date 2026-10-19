import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

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
    if (_lastStep != auth.step) {
      _lastStep = auth.step;
      _input.clear();
    }

    final (title, subtitle, label, keyboard, obscure) = switch (auth.step) {
      AuthStep.phone => ('Your phone', 'Include country code, e.g. +91…', 'Phone number', TextInputType.phone, false),
      AuthStep.code => ('Enter code', 'We sent a code to your Telegram app or SMS.', 'Code', TextInputType.number, false),
      AuthStep.password => (
          'Two-step verification',
          auth.hint?.isNotEmpty == true ? 'Hint: ${auth.hint}' : 'Enter your cloud password.',
          'Password',
          TextInputType.visiblePassword,
          true,
        ),
      _ => ('GlassGram', 'Connecting…', '', TextInputType.text, false),
    };

    final interactive = auth.step == AuthStep.phone || auth.step == AuthStep.code || auth.step == AuthStep.password;

    return GlassScaffold(
      background: const MeshBackground(),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
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
                    if (interactive) ...[
                      TextField(
                        controller: _input,
                        keyboardType: keyboard,
                        obscureText: obscure,
                        autofocus: true,
                        onSubmitted: (_) => _submit(auth.step),
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
                      FilledButton(
                        onPressed: auth.busy ? null : () => _submit(auth.step),
                        child: auth.busy
                            ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Text('Continue'),
                      ),
                    ] else
                      const Center(child: CircularProgressIndicator()),
                  ],
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
