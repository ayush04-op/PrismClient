import 'dart:async';

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

  /// Every object TDLib emits (updates, errors, responses).
  Stream<td.TdObject> get updates => _updates.stream;

  Future<void> start() async {
    if (_started) return;
    _started = true;

    await TdPlugin.initialize(); // ADAPTER (iOS: pass libtdjson name)
    _clientId = TdPlugin.instance.tdCreate(); // ADAPTER

    send(td.SetLogVerbosityLevel(newVerbosityLevel: 1));

    // Non-blocking poll (timeout 0). Phase 5: move to a dedicated isolate.
    _poller = Timer.periodic(const Duration(milliseconds: 25), (_) {
      for (var i = 0; i < 50; i++) {
        final event = TdPlugin.instance.tdReceive(0); // ADAPTER
        if (event == null) break;
        _updates.add(event);
      }
    });
  }

  void send(td.TdFunction function) {
    TdPlugin.instance.tdSend(_clientId, function); // ADAPTER
  }

  /// Answer TDLib's `authorizationStateWaitTdlibParameters`.
  Future<void> sendParameters() async {
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
        apiId: int.tryParse(dotenv.env['API_ID'] ?? '') ?? 0,
        apiHash: dotenv.env['API_HASH'] ?? '',
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
