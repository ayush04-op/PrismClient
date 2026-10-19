import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:path_provider/path_provider.dart';
import 'package:tdlib/td_api.dart' as td;
import 'package:tdlib/tdlib.dart';

/// Thin wrapper around TDLib using the `tdlib` package's documented API:
/// `tdCreate()`, `tdSend(clientId, request)` and `tdReceive(timeout)`.
class TdService {
  TdService._();
  static final TdService instance = TdService._();

  late final int _clientId;
  Timer? _poller;
  bool _started = false;

  final _updates = StreamController<td.TdObject>.broadcast();

  /// Last TDLib messages, shown on screen while debugging.
  final ValueNotifier<String> debugLog = ValueNotifier('starting…');

  /// Every object TDLib emits (updates, errors, responses).
  Stream<td.TdObject> get updates => _updates.stream;

  Future<void> start() async {
    if (_started) return;
    _started = true;

    try {
      await TdPlugin.initialize();
      _clientId = tdCreate();
      debugLog.value = 'client $_clientId created';
    } catch (e) {
      debugLog.value = 'init failed: $e';
      rethrow;
    }

    send(td.SetLogVerbosityLevel(newVerbosityLevel: 1));

    // Non-blocking poll (timeout 0). Moves to a background isolate in Phase 3+.
    _poller = Timer.periodic(const Duration(milliseconds: 20), (_) {
      try {
        for (var i = 0; i < 100; i++) {
          final event = tdReceive(0);
          if (event == null) break;
          if (event is! td.UpdateOption && event is! td.UpdateConnectionState) {
            debugLog.value = _short(event);
          }
          _updates.add(event);
        }
      } catch (e) {
        debugLog.value = 'receive error: $e';
      }
    });
  }

  String _short(td.TdObject o) {
    try {
      final s = jsonEncode(o.toJson());
      return s.length > 200 ? s.substring(0, 200) : s;
    } catch (_) {
      return o.runtimeType.toString();
    }
  }

  void send(td.TdFunction function) => tdSend(_clientId, function);

  /// Answer TDLib's `authorizationStateWaitTdlibParameters`.
  Future<void> sendParameters() async {
    final apiId = int.tryParse((dotenv.env['API_ID'] ?? '').trim()) ?? 0;
    final apiHash = (dotenv.env['API_HASH'] ?? '').trim();
    if (apiId == 0 || apiHash.isEmpty) {
      debugLog.value = 'API_ID / API_HASH missing in this build (check GitHub secrets)';
    }
    final dir = await getApplicationSupportDirectory();
    send(
      td.SetTdlibParameters(
        useTestDc: false,
        databaseDirectory: '${dir.path}/tdlib',
        filesDirectory: '${dir.path}/tdlib_files',
        databaseEncryptionKey: '',
        useFileDatabase: true,
        useChatInfoDatabase: true,
        useMessageDatabase: true,
        useSecretChats: false,
        apiId: apiId,
        apiHash: apiHash,
        systemLanguageCode: 'en',
        deviceModel: 'Android',
        systemVersion: 'Flutter',
        applicationVersion: '0.1.0',
        enableStorageOptimizer: true,
        ignoreFileNames: false,
      ),
    );
  }

  void dispose() {
    _poller?.cancel();
    _updates.close();
  }
}
