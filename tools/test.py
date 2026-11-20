#!/usr/bin/env python3
"""Compile the plugin against the SDK and the Android platform, and check the manifest."""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from android_sdk import android_platform
from plugin_assets import asset_files

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
javac = str(Path(java_home) / 'bin' / 'javac') if java_home else 'javac'
sdk_root = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'android-sdk'))))
platform = str(android_platform(sdk_root))
sources = [*sorted((root / 'sdk/src').rglob('*.java')), *sorted((root / 'src').rglob('*.java'))]
with tempfile.TemporaryDirectory(prefix='feder-test-') as directory:
    subprocess.run([javac, '--release', '8', '-Xlint:-options', '-cp', platform, '-d', directory, *map(str, sources)], check=True)

manifest = json.loads((root / 'kiosk-satellite-plugin.json').read_text(encoding='utf-8'))
assert manifest['schemaVersion'] == 1 and manifest['apiVersion'] == 1
assert re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', manifest['id'])
assert {'overlay', 'host.read', 'voice.skin', 'voice.text'} <= set(manifest['capabilities'])
names = {name for name, _ in asset_files(root)}
for key in ('icon', 'voiceSkinThumbnail'):
    assert 'assets/' + manifest[key] in names, f'missing asset for {key}'
assert (root / 'assets' / manifest['voiceSkinThumbnail']).stat().st_size <= 256 * 1024
print('ok')
