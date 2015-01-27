/* This program takes on stdin a single line of input,
 * in particular the "sync" line from record.log, and
 * displays the image that the sync point represents.
 */
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;

public class DisplaySyncPoint extends Canvas {
    private BufferedImage _im;
    private int _width, _height;

    public static void main(String[] args) {
	new DisplaySyncPoint().go();
    }

    private int getInt(Map<String, String> m, String key) {
	return Integer.parseInt(m.get(key));
    }

    public void go() {
	Map<String, String> m = getInput();

	int x0 = getInt(m, "x0");
	int y0 = getInt(m, "y0");
	int x1 = getInt(m, "x1");
	int y1 = getInt(m, "y1");

	_width = x1 - x0 + 1;
	_height = y1 - y0 + 1;

	_im = new BufferedImage(_width, _height, BufferedImage.TYPE_INT_ARGB);
	for (int x = x0; x <= x1; x++)
	    for (int y = y0; y <= y1; y++)
		_im.setRGB(x-x0, y-y0, getInt(m, "px" + x + "y" + y));

	System.out.println("Size: " + _width + "x" + _height);

	Frame frame = new Frame("display sync point");
	frame.add("Center", this);
	frame.setSize(_width + 50, _height + 50);
	frame.show();
    }

    public void paint(Graphics g) {
	g.drawImage(_im, 10, 10, null);
    }

    private Map<String, String> getInput() {
	Map<String, String> m = new HashMap<String, String>();

	String in;
	try {
	    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
	    in = br.readLine();
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}

	StringTokenizer st = new StringTokenizer(in);
	String delay = st.nextToken();

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
