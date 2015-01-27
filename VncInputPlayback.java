import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;

public class VncInputPlayback implements VncEventListener, ActionListener {
    // If fewer than SYNC_PIXEL_MATCH_THRESHOLD pixels in a sync point are
    // mismatched, then we say that the image looks good and we can proceed.
    public static final int SYNC_PIXEL_MATCH_THRESHOLD = 5;

    // Give up if we don't sync within SYNC_TIMEOUT_SEC seconds
    public static final int SYNC_TIMEOUT_SEC = 600;

    // Wait some time (msec) after sync matches
    public static final int WAIT_AFTER_SYNC = 200;

    private VncCanvas _vc;

    private FileReader _fr;
    private BufferedReader _br;
    private int _brLine;
    private javax.swing.Timer _timer;
    private FileWriter _auxwr;

    private boolean _syncWait;
    private long _syncWaitStarted;
    private long _syncWaitClear;
    private Map<String, String> _syncWaitOp;
    private Map<String, String> _nextOp;
    private long _lastEventCompletion;

    private MouseEvent _lastMouseMotion;
    private long _lastMouseMotionSent;

    private boolean _autoExit;

    private long _logStartTime;

    private final static boolean debug = false;
    private final static int _extraEventDelay = 0;
    private final static int _eventDelayPercent = 100;

    public VncInputPlayback(VncCanvas vc) {
	_vc = vc;
	_vc.setVncEventListener(this);

	// Start the recording, so we can sync with its startTime
	try {
	    _vc.viewer.checkRecordingStatus();
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	_logStartTime = System.currentTimeMillis();
	if (_vc.viewer.rfb.rec != null)
	    _logStartTime = _vc.viewer.rfb.rec.getStartTime();

	try {
	    _fr = new FileReader(new File(_vc.viewer.traceFile));
	    _br = new BufferedReader(_fr);
	    _brLine = 0;

	    _auxwr = new FileWriter(new File(_vc.viewer.recordFile + ".aux"));
	} catch (IOException e) {
	    e.printStackTrace();
	}

	_lastEventCompletion = System.currentTimeMillis();
	_timer = new javax.swing.Timer(20, this);
	_timer.start();
	_autoExit = false;

	_vc.setInputBlock(true);

	log("VNC input playback starting");
    }

    public void stop() {
	_vc.setVncEventListener(null);

	try {
	    _br.close();
	    _fr.close();
	    _auxwr.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}

	_timer.stop();

	System.out.println("VNC input playback stopping on line " + _brLine);
	_vc.setInputBlock(false);

	if (_autoExit)
	    System.exit(0);
    }

    private void log(String m) {
	System.out.println(m);
	try {
	    _auxwr.write(m + "\n");
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void setAutoExit(boolean v) {
	_autoExit = v;
    }

    private int getInt(Map<String, String> m, String key) {
	return Integer.parseInt(m.get(key));
    }

    public void actionPerformed(ActionEvent e) {
	long t = System.currentTimeMillis();

	if (_syncWait && _lastMouseMotion != null) {
	    if (t > _lastMouseMotionSent + 1000) {
		log("Trying to wiggle the mouse...");

		MouseEvent wiggle =
		    new MouseEvent(_vc,
				   _lastMouseMotion.getID(),
				   t,
				   _lastMouseMotion.getModifiers(),
				   _lastMouseMotion.getX() - 1,
				   _lastMouseMotion.getY(),
				   0, false,
				   _lastMouseMotion.getButton());
		_vc.processLocalMouseEvent(wiggle, true);
		_vc.processLocalMouseEvent(_lastMouseMotion, true);
		_lastMouseMotionSent = t;
	    }
	}

	if (_syncWait &&
	    (_syncWaitStarted > 0) &&
	    (t > _syncWaitStarted + SYNC_TIMEOUT_SEC * 1000))
	{
	    log("Timed out waiting for sync after " + (t - _syncWaitStarted) + " msec");
	    stop();
	}

	while (!_syncWait && _syncWaitClear < System.currentTimeMillis()) {
	    Map<String, String> m = next();
	    if (m == null) {
		stop();
		return;
	    }

	    long ts = Long.parseLong(m.get("run-at"));
	    if (ts < System.currentTimeMillis()) {
		doEvent(m);
		_lastEventCompletion = System.currentTimeMillis();
		/* _lastEventCompletion = ts; */
	    } else {
		pushBack(m);
		return;
	    }
	}
    }

    private long logTime() {
	return System.currentTimeMillis() - _logStartTime;
    }

    private void doEvent(Map<String, String> m) {
	String type = m.get("type");
	if (type.equals("sync")) {
	    _syncWait = true;
	    _syncWaitOp = m;
	    _syncWaitStarted = 0;
	    _syncWaitClear = 0;

	    log("Waiting for sync... at " + logTime());
	    checkSync();
	    _syncWaitStarted = System.currentTimeMillis();
	} else if (type.equals("java.awt.event.MouseEvent")) {
	    int id = getInt(m, "id");
	    int modifiers = getInt(m, "modifiers");
	    int x = getInt(m, "x");
	    int y = getInt(m, "y");
	    int button = getInt(m, "button");

	    MouseEvent me = new MouseEvent(_vc, id, System.currentTimeMillis(),
					   modifiers, x, y, 0, false, button);
	    _vc.processLocalMouseEvent(me, true);
	    if (button != 0)
		log("== sending mouse click event at " +
				   logTime());

	    if (id == MouseEvent.MOUSE_DRAGGED ||
		id == MouseEvent.MOUSE_MOVED) {
		_lastMouseMotion = me;
		_lastMouseMotionSent = System.currentTimeMillis();
	    }
	} else if (type.equals("java.awt.event.KeyEvent")) {
	    int id = getInt(m, "id");
	    int modifiers = getInt(m, "modifiers");
	    int keyCode = getInt(m, "keycode");
	    char keyChar = (char) getInt(m, "keychar");

	    KeyEvent ke = new KeyEvent(_vc, id, System.currentTimeMillis(),
				       modifiers, keyCode, keyChar);
	    _vc.processLocalKeyEvent(ke);
	    log("== sending keyboard event at " + logTime());
	} else {
	    log("unknown type of event: " + type);
	}
    }

    private boolean syncOk() {
	BufferedImage im = _vc.getBufferedImage();

	int x0 = getInt(_syncWaitOp, "x0");
	int x1 = getInt(_syncWaitOp, "x1");
	int y0 = getInt(_syncWaitOp, "y0");
	int y1 = getInt(_syncWaitOp, "y1");

	//log("Checking sync...");

	Map<String, String> newMap = new HashMap<String, String>();
	newMap.put("x0", "" + x0);
	newMap.put("x1", "" + x1);
	newMap.put("y0", "" + y0);
	newMap.put("y1", "" + y1);

	int mismatchCount = 0;
	int pixelCount = (x1 - x0 + 1) * (y1 - y0 + 1);

	for (int x = x0; x <= x1; x++) {
	    for (int y = y0; y <= y1; y++) {
		int syncPixel = getInt(_syncWaitOp, "px" + x + "y" + y);
		int realPixel = im.getRGB(x, y);
		newMap.put("px" + x + "y" + y, "" + realPixel);
		if (syncPixel != realPixel) {
		    mismatchCount++;
		}
	    }
	}

	log("Sync mismatches: " + mismatchCount + "/" + pixelCount +
			   ", threshold " + SYNC_PIXEL_MATCH_THRESHOLD);
	if (mismatchCount >= SYNC_PIXEL_MATCH_THRESHOLD && debug) {
	    log("Original image:");
	    printMap(_syncWaitOp);
	    log("Current image:");
	    printMap(newMap);
	}

	return (mismatchCount < SYNC_PIXEL_MATCH_THRESHOLD);
    }

    private void checkSync() {
	if (_syncWait && syncOk()) {
	    long syncWaitTime = 0;

	    if (_syncWaitStarted > 0) {
		syncWaitTime = System.currentTimeMillis() - _syncWaitStarted;
		_syncWaitClear = System.currentTimeMillis() + WAIT_AFTER_SYNC;
	    }

	    log("Sync ok after " + syncWaitTime + " msec");
	    log("Sync ok at " + logTime());
	    _syncWait = false;
	    _lastEventCompletion = System.currentTimeMillis();
	}
    }

    private void printMap(Map<String, String> m) {
	String s = "0";
	for (String k: m.keySet())
	    s += " " + k + "=" + m.get(k);
	log(s);
    }

    public void screenEvent(int x, int y, int w, int h) {
	checkSync();
    }

    public void mouseEvent(MouseEvent e) {
    }

    public void keyEvent(KeyEvent e) {
    }

    private Map<String, String> next() {
	Map<String, String> m;

	if (_nextOp != null)
	    m = _nextOp;
	else
	    m = readNext(System.currentTimeMillis());

	_nextOp = null;
	return m;
    }

    private void pushBack(Map<String, String> m) {
	if (_nextOp != null)
	    throw new RuntimeException("pushback: nextop is not null");

	_nextOp = m;
    }

    private Map<String, String> readNext(long lastCompletion) {
	Map<String, String> m = new HashMap<String, String>();

	String in;

	try {
	    in = _br.readLine();
	    _brLine++;
	} catch (IOException e) {
	    e.printStackTrace();
	    return null;
	}

	if (in == null)
	    return null;

	StringTokenizer st = new StringTokenizer(in);
	String delay = st.nextToken();
	m.put("run-at", "" + (_lastEventCompletion + _extraEventDelay + _eventDelayPercent * Integer.parseInt(delay) / 100));

	while (st.hasMoreTokens()) {
	    String t = st.nextToken();

	    StringTokenizer st2 = new StringTokenizer(t, "=");
	    String key = st2.nextToken();
	    String value = st2.nextToken();
	    m.put(key, value);
	}

	return m;
    }
}
