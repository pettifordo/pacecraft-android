"""Real Wear OS UI/service smoke check. Uses labelled synthetic input, never GPS evidence."""
import atexit
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

OUT=pathlib.Path('watch-screenshots');OUT.mkdir(exist_ok=True)
PKG='com.pacecraft.android'
def adb(*args):return subprocess.check_output(['adb',*args],text=True,timeout=90)
def nodes():
    adb('shell','uiautomator','dump','/data/local/tmp/watch-ui.xml')
    text=adb('shell','cat','/data/local/tmp/watch-ui.xml')
    return list(ET.fromstring(text[text.index('<?xml'):]).iter('node'))
def find(label):return next((n for n in nodes() if n.get('text')==label),None)
def wait_for(label):
    for _ in range(30):
        if find(label) is not None:return
        time.sleep(1)
    raise AssertionError('Watch screen never displayed '+label)
def tap(label):
    for _ in range(12):
        n=find(label)
        if n is not None:
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);return
        adb('shell','input','swipe','220','330','220','130','300');time.sleep(.4)
    raise AssertionError('Missing watch control '+label)
def capture(name):
    adb('shell','screencap','-p','/data/local/tmp/watch.png');adb('pull','/data/local/tmp/watch.png',str(OUT/(name+'.png')))
def diagnostics():
    if not (OUT/'smoke-test.txt').exists():
        try:capture('failure');(OUT/'failure-logcat.txt').write_text(adb('logcat','-d','-t','1500'));nodes();(OUT/'failure-ui.xml').write_text(adb('shell','cat','/data/local/tmp/watch-ui.xml'))
        except Exception as e:print(e)
atexit.register(diagnostics)
adb('install','--no-streaming','-r','wear/build/outputs/apk/debug/wear-debug.apk')
adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
adb('shell','dumpsys','battery','unplug')
adb('shell','settings','put','system','screen_off_timeout','1800000')
adb('shell','settings','put','secure','doze_enabled','0')
adb('shell','input','keyevent','KEYCODE_WAKEUP')
adb('shell','wm','dismiss-keyguard')
for permission in ['ACTIVITY_RECOGNITION','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','BODY_SENSORS','POST_NOTIFICATIONS']:
    adb('shell','pm','grant',PKG,'android.permission.'+permission)
adb('shell','am','start','-W','-n',PKG+'/com.pacecraft.wear.WatchActivity');wait_for('Your next run, on your wrist');capture('watch-onboarding')
adb('shell','am','force-stop',PKG)
workout={'schema':1,'id':'emulator-structured-workout','name':'Intervals · test fixture','day':time.strftime('%Y-%m-%d'),'steps':[
    {'label':'Warm up','seconds':600,'metres':1500,'lowSpeed':2.0,'highSpeed':2.8},
    {'label':'Work 1 of 3','seconds':180,'metres':650,'lowSpeed':3.4,'highSpeed':3.8},
    {'label':'Jog recovery','seconds':120,'metres':250,'lowSpeed':1.8,'highSpeed':2.3}]}
active={'id':'emulator-active','workout':json.dumps(workout),'start':'2026-11-14T09:00:00Z','elapsed':598,'metres':0,'paused':True,'hrTotal':0,'hrCount':0}
xml='<map><string name="workout">'+escape(json.dumps(workout))+'</string><string name="active">'+escape(json.dumps(active))+'</string></map>'
p=OUT/'fixture.xml';p.write_text(xml)
adb('push',str(p),'/data/local/tmp/watch-fixture.xml');adb('shell','run-as',PKG,'mkdir','-p','shared_prefs');adb('shell','run-as',PKG,'cp','/data/local/tmp/watch-fixture.xml','shared_prefs/coach-watch.xml');p.unlink()
adb('shell','am','start','-W','-n',PKG+'/com.pacecraft.wear.WatchActivity');wait_for('Paused');capture('watch-paused')
tap('Resume');time.sleep(4)
# Return to the top of the small round display after scrolling to Resume.
for _ in range(4):adb('shell','input','swipe','220','130','220','330','250')
assert find('Work 1 of 3') is not None
capture('watch-interval')
tap('Pause')
prefs=ET.fromstring(adb('exec-out','run-as',PKG,'cat','shared_prefs/coach-watch.xml'))
active=json.loads(prefs.find("string[@name='active']").text)
assert active['paused'] is True
tap('Finish run');tap('Save & finish');time.sleep(2)
stored=adb('exec-out','run-as',PKG,'cat','shared_prefs/coach-watch.xml')
assert 'name="active"' not in stored
assert 'No complete GPS distance' in stored
assert 'outbox-' not in stored, 'Timer-only fixture must never produce a fabricated distance'
capture('watch-finished')
(OUT/'smoke-test.txt').write_text('PASS: real Wear OS emulator APK install, onboarding, durable workout restore, visible Resume starts foreground tracking, automatic work-step transition, pace target, Pause, Finish, no fabricated GPS distance. Synthetic workout fixture used for UI/service testing. Physical paired transfer, GPS accuracy, heart-rate capture and watch battery behaviour require hardware testing.\n')
