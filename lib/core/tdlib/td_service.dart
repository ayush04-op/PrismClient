import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:path_provider/path_provider.dart';
import 'package:tdlib/td_api.dart' as td;
import 'package:tdlib/td_client.dart';
import 'package:tdlib/tdlib.dart';

/// Thin wrapper around the TDLib JSON client.
///
/// ADAPTER NOTE: the three calls marked `// ADAPTER` are the only places that
/// touch the `tdlib` package's low-level client API. If the package version
/// differs, adjust only those lines.
class TdService {
  TdService._();
  static final TdService instance = TdService._();

  late final int _clientId;
  Timer? _poller;
  bool _started = false;

  final _updates = StreamController<td.TdObject>.broadcast();

  /// Last raw TDLib messages, shown on screen while debugging.
  final ValueNotifier<String> debugLog = ValueNotifier('starting…');

  /// Every object TDLib emits (updates, errors, responses).
  Stream<td.TdObject> get updates => _updates.stream;

  Future<void> start() async {
    if (_started) return;
    _started = true;

    try {
      await TdPlugin.initialize(); // ADAPTER (iOS: pass libtdjson name)
    } catch (e) {
      debugLog.value = 'init failed: $e';
      rethrow;
    }
    _clientId = TdPlugin.instance.tdCreate(); // ADAPTER

    send(td.SetLogVerbosityLevel(newVerbosityLevel: 1));

    // Non-blocking poll (timeout 0). Phase 5: move to a dedicated isolate.
    _poller = Timer.periodic(const Duration(milliseconds: 25), (_) {
      for (var i = 0; i < 50; i++) {
        final raw = TdPlugin.instance.tdReceive(0); // ADAPTER
        if (raw == null) break;
        if (!raw.contains('updateOption') && !raw.contains('updateConnectionState')) {
          debugLog.value = raw.length > 220 ? raw.substring(0, 220) : raw;
        }
        final obj = td.convertToObject(raw);
        if (obj != null) _updates.add(obj);
      }
    });
  }

  void send(td.TdFunction function) {
    TdPlugin.instance.tdSend(_clientId, jsonEncode(function.toJson())); // ADAPTER
  }

  /// Answer TDLib's `authorizationStateWaitTdlibParameters`.
  Future<void> sendParameters() async {
    debugLog.value = 'sending parameters (api_id length ${dotenv.env['API_ID']?.trim().length ?? 0})';
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
        apiId: int.tryParse((dotenv.env['API_ID'] ?? '').trim()) ?? 0,
        apiHash: (dotenv.env['API_HASH'] ?? '').trim(),
        systemLanguageCode: 'en',
        deviceModel: 'GlassGram',
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
