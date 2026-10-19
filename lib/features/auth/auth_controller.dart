import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:tdlib/td_api.dart' as td;

import '../../core/tdlib/td_service.dart';

enum AuthStep { loading, phone, code, password, ready, closed }

class AuthState {
  const AuthState({this.step = AuthStep.loading, this.error, this.busy = false, this.hint});

  final AuthStep step;
  final String? error;
  final bool busy;
  final String? hint; // e.g. 2FA password hint

  AuthState copyWith({AuthStep? step, String? error, bool? busy, String? hint, bool clearError = false}) =>
      AuthState(
        step: step ?? this.step,
        error: clearError ? null : (error ?? this.error),
        busy: busy ?? this.busy,
        hint: hint ?? this.hint,
      );
}

class AuthController extends Notifier<AuthState> {
  StreamSubscription<td.TdObject>? _sub;
  final _td = TdService.instance;
  String? _pendingPhone; // phone typed before TDLib was ready

  @override
  AuthState build() {
    ref.onDispose(() => _sub?.cancel());
    _init();
    return const AuthState();
  }

  Future<void> _init() async {
    try {
      await _td.start();
    } catch (e) {
      state = state.copyWith(error: '$e');
      return;
    }
    _sub = _td.updates.listen(_onEvent);
    _td.send(td.GetAuthorizationState());
  }

  void _onEvent(td.TdObject e) {
    if (e is td.UpdateAuthorizationState) {
      _onAuthState(e.authorizationState);
    } else if (e is td.AuthorizationState) {
      _onAuthState(e); // response to GetAuthorizationState
    } else if (e is td.TdError) {
      state = state.copyWith(busy: false, error: e.message);
    }
  }

  void _onAuthState(td.AuthorizationState s) {
    if (s is td.AuthorizationStateWaitTdlibParameters) {
      _td.sendParameters();
    } else if (s is td.AuthorizationStateWaitPhoneNumber) {
      state = state.copyWith(step: AuthStep.phone, busy: false);
      final pending = _pendingPhone;
      if (pending != null) {
        _pendingPhone = null;
        submitPhone(pending);
      }
    } else if (s is td.AuthorizationStateWaitCode) {
      state = state.copyWith(step: AuthStep.code, busy: false, clearError: true);
    } else if (s is td.AuthorizationStateWaitPassword) {
      state = state.copyWith(step: AuthStep.password, busy: false, hint: s.passwordHint, clearError: true);
    } else if (s is td.AuthorizationStateReady) {
      state = state.copyWith(step: AuthStep.ready, busy: false, clearError: true);
    } else if (s is td.AuthorizationStateClosed) {
      state = state.copyWith(step: AuthStep.closed);
    }
  }

  void submitPhone(String phone) {
    state = state.copyWith(busy: true, clearError: true);
    if (state.step == AuthStep.loading) {
      _pendingPhone = phone; // sent as soon as TDLib is ready
      return;
    }
    _td.send(td.SetAuthenticationPhoneNumber(phoneNumber: phone.trim(), settings: null));
  }

  void submitCode(String code) {
    state = state.copyWith(busy: true, clearError: true);
    _td.send(td.CheckAuthenticationCode(code: code.trim()));
  }

  void submitPassword(String password) {
    state = state.copyWith(busy: true, clearError: true);
    _td.send(td.CheckAuthenticationPassword(password: password));
  }
}

final authProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);
