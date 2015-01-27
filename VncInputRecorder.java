import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;

public class VncInputRecorder implements VncEventListener, ActionListener {
    public static final int SYNC_AREA_SIZE = 5;
    public static final int SYNC_WATCH_AREA_SIZE = SYNC_AREA_SIZE + 15;

    private long lastLogTime;
    private VncCanvas _vc;

    private FileOutputStream _fos;
    private BufferedOutputStream _bos;
    private PrintStream _out;

    private long _lastButtonPress;

    private ScreenUpdateWatcher _updatesSinceSync;

    private javax.swing.Timer _quiesceTimer;
    private Point _syncPoint;
    private Map<String, String> _syncEvent;

    public VncInputRecorder(VncCanvas vc) {
	_vc = vc;
	_vc.setVncEventListener(this);

	try {
	    _fos = new FileOutputStream(new File(_vc.viewer.traceFile));
	    _bos = new BufferedOutputStream(_fos);
	    _out = new PrintStream(_bos);
	} catch (IOException e) {
	    e.printStackTrace();
	}

	_updatesSinceSync = new ScreenUpdateWatcher(new Rectangle(0, 0, 0, 0), 0);
	lastLogTime = System.currentTimeMillis();

	System.out.println("VNC input recorder starting");
    }

    public void stop() {
	_vc.setVncEventListener(null);

	try {
	    _out.close();
	    _bos.close();
	    _fos.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}

	System.out.println("VNC input recorder stopping");
    }

    public long getLogDeltaTime(long ts) {
	long dt = ts - lastLogTime;
	lastLogTime = ts;
	return dt;
    }

    public void screenEvent(int x, int y, int w, int h) {
	if (_quiesceTimer != null)
	    System.out.println("screen update @ " + System.currentTimeMillis() + " for <" + x + ", " + y + "> w="+w+ ", h=" + h);

	Rectangle r = new Rectangle(x, y, w, h);
	_updatesSinceSync.markUpdated(r);
    }

    private int fixRange(int v, int min, int max) {
	if (v < min)
	    return min;
	if (v > max)
	    return max;
	return v;
    }

    private void syncEvent(Point p, Map<String, String> em) {
	_vc.holdInput();

	_syncPoint = p;
	_syncEvent = em;

	// Empirically, the VMware VNC server sends back all the frame buffer
	// updates within ~100 msec (on a 100Mbps LAN connection)
	_quiesceTimer = new javax.swing.Timer(150, this);
	_quiesceTimer.start();
	System.out.println("scheduling timer to quiesce at " + System.currentTimeMillis());
    }

    public void actionPerformed(ActionEvent e) {
	System.out.println("done waiting for quiescing at " + System.currentTimeMillis());

	_quiesceTimer.stop();
	_quiesceTimer = null;
	Point p = _syncPoint;
	BufferedImage im = _vc.getBufferedImage();

	int imWidth = im.getWidth();
	int imHeight = im.getHeight();

	int x0 = fixRange(p.x - SYNC_AREA_SIZE, 0, imWidth - 1);
	int x1 = fixRange(p.x + SYNC_AREA_SIZE, 0, imWidth - 1);
	int y0 = fixRange(p.y - SYNC_AREA_SIZE, 0, imHeight - 1);
	int y1 = fixRange(p.y + SYNC_AREA_SIZE, 0, imHeight - 1);

	Rectangle syncR = new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
	if (_updatesSinceSync.isUnchanged(syncR)) {
	    _vc.releaseInput();
	    return;
	}

	Map<String, String> m = new HashMap<String, String>();
	m.put("type", "sync");
	m.put("x0", "" + x0);
	m.put("y0", "" + y0);
	m.put("x1", "" + x1);
	m.put("y1", "" + y1);

	for (int x = x0; x <= x1; x++)
	    for (int y = y0; y <= y1; y++)
		m.put("px" + x + "y" + y, "" + im.getRGB(x, y));
	long ts = Long.parseLong(_syncEvent.get("when"));
	log(m, ts);
	log(_syncEvent, ts);

	Rectangle newWatch = new Rectangle(p.x - SYNC_WATCH_AREA_SIZE,
					   p.y - SYNC_WATCH_AREA_SIZE,
					   SYNC_WATCH_AREA_SIZE * 2,
					   SYNC_WATCH_AREA_SIZE * 2);
	_updatesSinceSync = new ScreenUpdateWatcher(newWatch, 100);

	_vc.releaseInput();
    }

    public void mouseEvent(MouseEvent e) {
	int id = e.getID();
	int button = e.getButton();
	boolean clickRelease = false;

	if (id == MouseEvent.MOUSE_PRESSED)
	    _lastButtonPress = e.getWhen();

	if (id == MouseEvent.MOUSE_RELEASED) {
	    if (e.getWhen() < _lastButtonPress + 200) {
		System.out.println("special-casing click release");
		clickRelease = true;
	    }
	}

	Map<String, String> em = logEvent(e, "x", e.getX(), "y", e.getY(), "button", e.getButton());

	if (button != MouseEvent.NOBUTTON && !clickRelease) {
	    syncEvent(e.getPoint(), em);
	} else {
	    log(em, Long.parseLong(em.get("when")));
	}
    }

    public void keyEvent(KeyEvent e) {
	log(logEvent(e, "keycode", e.getKeyCode(), "keychar", (int) e.getKeyChar()), e.getWhen());
    }

    private Map<String, String> logEvent(InputEvent e, Object... args) {
	Map<String, String> m = new HashMap<String, String>();

	for (int i = 0; i < args.length; i += 2)
	    m.put(args[i].toString(), args[i+1].toString());

	m.put("type", e.getClass().getName());
	m.put("id", "" + e.getID());
	m.put("modifiers", "" + e.getModifiers());
	m.put("when", "" + e.getWhen());

	return m;
    }

    private void log(Map<String, String> m, long ts) {
	long dt = getLogDeltaTime(ts);
	_out.print(dt);
	for (String k: m.keySet())
	    _out.print(" " + k + "=" + m.get(k));
	_out.println("");
    }
}
