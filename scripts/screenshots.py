"""Exercise the installed app and capture real Android emulator screenshots."""
import atexit
import sys
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

def find(label, prefix=False, contains=False):
    for node in nodes():
        texts = [node.get("text", ""), node.get("content-desc", "")]
        if any(label in text if contains else text.startswith(label) if prefix else text == label for text in texts):
            return node
    return None

def wait_for(label, prefix=False, contains=False):
    for _ in range(40):
        node = find(label, prefix, contains)
        if node is not None:
            return node
        time.sleep(1)
    print('Visible labels:', [n.get('text') for n in nodes() if n.get('text')], flush=True)
    raise AssertionError('Screen never displayed: ' + label)

def tap(label, prefix=False, contains=False):
    node = wait_for(label, prefix, contains)
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def scroll_to(label, prefix=False, bottom=True, contains=False):
    for _ in range(12):
        if find(label, prefix, contains) is not None:
            return
        start,end = ('1450','450') if bottom else ('450','1450')
        adb('shell', 'input', 'swipe', '540', start, '540', end, '300')
        time.sleep(.5)
    print('Visible after scroll:', [n.get('text') for n in nodes() if n.get('text')], flush=True)
    raise AssertionError('Could not scroll to: ' + label)

def capture(name, top=True):
    if top:
        adb('shell', 'input', 'swipe', '540', '350', '540', '1550', '300')
    time.sleep(1)
    adb('shell', 'screencap', '-p', '/data/local/tmp/pacecraft.png')
    adb('pull', '/data/local/tmp/pacecraft.png', str(OUT / (name + '.png')))
    assert (OUT / (name + '.png')).stat().st_size > 15000, 'Empty screenshot: ' + name

def save_diagnostics():
    if not (OUT/'smoke-test.txt').exists():
        try:
            nodes()
            (OUT/'failure-ui.xml').write_text(adb('shell','cat','/data/local/tmp/pacecraft-ui.xml'))
            (OUT/'failure-logcat.txt').write_text(adb('logcat','-d','-t','1500'))
            capture('failure',top=False)
        except Exception as e:
            print('Diagnostic capture:',e)
atexit.register(save_diagnostics)

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
for label,heading,name in [('Plan','Your whole plan','plan'), ('Progress','Built on','progress'), ('History','Your running','history'), ('Coach','A little context','coach')]:
    tap(label)
    wait_for(heading, prefix=True)
    if label == 'Plan':
        wait_for('Share plan PDF')
        scroll_to('WEEKLY VOLUME')
    if label == 'Plan':
        scroll_to('Week by week')
    capture(name,top=(label != 'Plan'))

# Demo data must never replace or enter the real local cache.
stored = json.loads(adb('exec-out', 'run-as', 'com.pacecraft.android', 'cat', 'files/pacecraft-v1.json'))
assert stored['runs'] == [] and stored['cross'] == []
tap('Plan')
scroll_to('WEEKLY VOLUME')
scroll_to('Expand all')
tap('Expand all')
scroll_to('Week 2', prefix=True)
node=wait_for('Week 2', prefix=True)
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
if y1>350:
    adb('shell','input','swipe','540',str((y1+y2)//2),'540','300','300')
capture('plan-week',top=False)
# Inspect a real interval prescription later in the generated training block.
scroll_to('Collapse all',bottom=False)
tap('Collapse all')
scroll_to('Exit demo',bottom=False)
scroll_to('Includes intervals',contains=True)
tap('Includes intervals',contains=True)
scroll_to('VO₂ intervals',contains=True)
tap('VO₂ intervals',contains=True)
wait_for('Done')
scroll_to('Jog recovery ·',prefix=True)
wait_for('Work ·',prefix=True)
wait_for('Jog recovery ·',prefix=True)
capture('intervals',top=False)
tap('Done')
scroll_to('Exit demo',bottom=False)
tap('Exit demo')
tap('History')
wait_for('No runs yet')
# Start from self-report without touching the demo cache.
tap('Next run')
scroll_to('Start without run history')
tap('Start without run history')
wait_for('Start where you are')
capture('starting-ability',top=False)
tap('Build my starting plan')
wait_for('STARTING PLAN')
capture('starter-next-run')
scroll_to('Adjust length, pace or day')
tap('Adjust length, pace or day')
wait_for('Adjust this session')
capture('session-adjustment',top=False)
tap('Cancel')
# Exercise Android FileProvider sharing and inspect the generated private files.
tap('Plan')
wait_for('Share plan PDF')
tap('Share plan PDF')
time.sleep(2)
adb('shell','input','keyevent','KEYCODE_BACK')
pdf=subprocess.check_output(['adb','exec-out','run-as','com.pacecraft.android','cat','cache/exports/CoachMeSimple-plan.pdf'])
assert pdf.startswith(b'%PDF') and len(pdf)>1000
scroll_to('Next week → calendar')
tap('Next week → calendar')
time.sleep(2)
adb('shell','input','keyevent','KEYCODE_BACK')
calendar=adb('exec-out','run-as','com.pacecraft.android','cat','cache/exports/CoachMeSimple-next-week.ics')
assert 'BEGIN:VCALENDAR' in calendar and 'BEGIN:VEVENT' in calendar and 'DESCRIPTION:' in calendar
assert all(len(line.encode('utf-8'))<=75 for line in calendar.splitlines())
tap('Next run')
# Relaunch confirms persistence; a completed watch inbox record must import once.
stored=json.loads(adb('exec-out','run-as','com.pacecraft.android','cat','files/pacecraft-v1.json'))
assert stored['starter']['ability']=='JOG5' and stored['runs']==[]
adb('shell','am','force-stop','com.pacecraft.android')
fixture={'schema':1,'id':'emulator-watch-run','start':__import__('datetime').datetime.now(__import__('datetime').timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z'),'metres':1200,'seconds':1200,'hr':None,'workoutId':'smoke'}
fixture['start']=(__import__('datetime').datetime.now(__import__('datetime').timezone.utc)-__import__('datetime').timedelta(hours=2)).isoformat().replace('+00:00','Z')
path=OUT/'watch-return-fixture.json';path.write_text(json.dumps(fixture))
adb('push',str(path),'/data/local/tmp/watch-return.json')
adb('shell','run-as','com.pacecraft.android','mkdir','-p','files/wear-inbox')
adb('shell','run-as','com.pacecraft.android','cp','/data/local/tmp/watch-return.json','files/wear-inbox/emulator-watch-run.json')
adb('shell','am','start','-W','-n','com.pacecraft.android/.MainActivity')
wait_for('STARTING PLAN')
time.sleep(5)
for _ in range(2):
    stored=json.loads(adb('exec-out','run-as','com.pacecraft.android','cat','files/pacecraft-v1.json'))
    assert len([r for r in stored['runs'] if r['id']=='wear:emulator-watch-run'])==1
    adb('shell','am','force-stop','com.pacecraft.android')
    adb('shell','am','start','-W','-n','com.pacecraft.android/.MainActivity')
    time.sleep(5)
path.unlink()
(OUT / 'smoke-test.txt').write_text(
    'PASS: installed APK, onboarding, demo entry, next-run screen, full-plan projection with prescribed paces, interval reps and recovery paces, progress, history, coach, '
    'demo isolation in local persistence, demo exit, empty real history.\n'
    'PASS: PDF/calendar sharing, starter onboarding, run/walk steps, adjustment preview, persistence and exactly-once completed-watch-run inbox import.\n'
    'Images are unedited screencap output from the running native app.\n')
print('All emulator screenshot and smoke checks passed.')
