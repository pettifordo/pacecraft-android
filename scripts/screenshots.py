"""Exercise the installed app and capture real Android emulator screenshots."""
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path('emulator-screenshots')
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=90)

def nodes():
    adb('shell', 'uiautomator', 'dump', '/data/local/tmp/pacecraft-ui.xml')
    xml = adb('shell', 'cat', '/data/local/tmp/pacecraft-ui.xml')
    return list(ET.fromstring(xml[xml.index('<?xml'):]).iter('node'))

def find(label, prefix=False):
    for node in nodes():
        text = node.get('text', '')
        if (text.startswith(label) if prefix else text == label):
            return node
    return None

def wait_for(label, prefix=False):
    for _ in range(12):
        node = find(label, prefix)
        if node is not None:
            return node
        time.sleep(1)
    raise AssertionError('Screen never displayed: ' + label)

def tap(label, prefix=False):
    node = wait_for(label, prefix)
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def capture(name):
    adb('shell', 'screencap', '-p', '/data/local/tmp/pacecraft.png')
    adb('pull', '/data/local/tmp/pacecraft.png', str(OUT / (name + '.png')))
    assert (OUT / (name + '.png')).stat().st_size > 15000, 'Empty screenshot: ' + name

adb('install', '--no-streaming', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
adb('shell', 'svc', 'power', 'stayon', 'true')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
adb('shell', 'am', 'start', '-W', '-n', 'com.pacecraft.android/.MainActivity')
wait_for('Your next chapter', prefix=True)
capture('onboarding')
for _ in range(5):
    if find('Explore with sample history', prefix=True) is not None:
        break
    adb('shell', 'input', 'swipe', '360', '1200', '360', '400', '350')
    time.sleep(1)
tap('Explore with sample history', prefix=True)
wait_for('DEMO', prefix=True)
wait_for('Make this', prefix=True)
capture('next-run')
for label,heading,name in [('Progress','Built on','progress'), ('History','Your running','history'), ('Coach','A little context','coach')]:
    tap(label)
    wait_for(heading, prefix=True)
    capture(name)

# Demo data must never replace or enter the real local cache.
stored = json.loads(adb('exec-out', 'run-as', 'com.pacecraft.android', 'cat', 'files/pacecraft-v1.json'))
assert stored['runs'] == [] and stored['cross'] == []
tap('Exit demo')
tap('History')
wait_for('No runs yet')
(OUT / 'smoke-test.txt').write_text(
    'PASS: installed APK, onboarding, demo entry, next-run screen, progress, history, coach, '
    'demo isolation in local persistence, demo exit, empty real history.\n'
    'Images are unedited screencap output from the running native app.\n')
print('All emulator screenshot and smoke checks passed.')
