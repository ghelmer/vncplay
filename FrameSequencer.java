import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

public class FrameSequencer implements VncEventListener {
    public static final int SCALEDOWN = 4;

    private FrameSequence _seq;
    private File _f;

    private VncViewer _viewer;
    private VncCanvas _vc;
    private long _curTs;
    private int _bytesSent = 0;

    private long _lastTsPrinted = 0;

    public FrameSequencer(File f) {
	_f = f;
    }

    public FrameSequence getFrameSequence() {
	return _seq;
    }

    public void screenEvent(int x, int y, int w, int h) {
	//System.out.println("update x="+x+", y="+y+", w="+w+", h="+h+", ts="+_curTs);

	FrameUpdate u = new FrameUpdate(x / SCALEDOWN, y / SCALEDOWN,
					(x + w) / SCALEDOWN - x / SCALEDOWN,
					(y + h) / SCALEDOWN - y / SCALEDOWN,
					_curTs);

	BufferedImage im = _vc.getBufferedImage();
	for (int mx = x / SCALEDOWN; mx < (x + w) / SCALEDOWN; mx++)
	    for (int my = y / SCALEDOWN; my < (y + h) / SCALEDOWN; my++)
		u.set(mx, my, im.getRGB(mx * SCALEDOWN, my * SCALEDOWN));

	_seq.add(u);
	//System.out.println("added frame update ts=" + _curTs);
    }

    public void mouseEvent(MouseEvent e) {}
    public void keyEvent(KeyEvent e) {}

    public void create() throws IOException {
	FileInputStream fis = new FileInputStream(_f);
	BufferedInputStream bis = new BufferedInputStream(fis);
	DataInputStream dis = new DataInputStream(bis);

	try {
	    create(dis);
	} finally {
	    dis.close();
	    bis.close();
	    fis.close();
	}
    }

    private void create(DataInputStream is) throws IOException {
	// skip version header
	long s = is.skip(12);
	if (s != 12)
	    throw new RuntimeException("couldn't skip 12");

	_seq = new FrameSequence();
	_viewer = new VncViewer();
	_viewer.mainArgs = new String[] { "host", "pipe-magic",
					  "port", "12345",
					  "password", "dummy" };
	_viewer.inAnApplet = false;
	_viewer.inSeparateFrame = true;
	_viewer.doExit = false;
	_viewer.init();
	_viewer.start();

	try {
	    _viewer._rfbLatch.await();
	} catch (InterruptedException e) {
	    throw new RuntimeException(e);
	}
	System.out.println("rfb latch done");

	processBlob(is);

	try {
	    _viewer._vcLatch.await();
	} catch (InterruptedException e) {
	    throw new RuntimeException(e);
	}
	System.out.println("vc latch done");

	_vc = _viewer.vc;
	_vc.setVncEventListener(this);
	_vc.repaintEnabled = false;
	_viewer.deferUpdateRequests = 0;

	try {
	    for (;;) {
		processBlob(is);
	    }
	} catch (EOFException e) {
	    System.out.println("EOF: done");
	}

	_seq.setSize(_vc.rfb.framebufferWidth / SCALEDOWN, _vc.rfb.framebufferHeight / SCALEDOWN);
	_viewer.disconnect();
    }

    private void processBlob(DataInputStream is)
	throws IOException, EOFException
    {
	int len = is.readInt();
	int fileBlockLen = (len + 3) & 0x7FFFFFFC;
	byte[] data = new byte[fileBlockLen];
	is.readFully(data);
	_curTs = is.readInt();

	if (_curTs > _lastTsPrinted + 30000) {
	    System.out.println(" ... processing ts = " + _curTs);
	    _lastTsPrinted = _curTs;
	}

	// feed this blob into the RfbProto/VncViewer
	_viewer.rfb.pipeOs.write(data, 0, len);
	_viewer.rfb.pipeOs.flush();
	_bytesSent += len;

	synchronized (_viewer.rfb) {
	    while (_viewer.rfb.bytesProcessed != _bytesSent) {
		try {
		    _viewer.rfb.wait();
		} catch (InterruptedException e) {}
	    }
	}
    }
}
