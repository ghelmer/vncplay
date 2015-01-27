import java.util.*;

public class FrameSequence implements Iterable<FrameUpdate> {
    public static final int MAX_COW_COUNT = 100;

    private List<FrameUpdate> _updates;
    private int _width, _height;
    private int _cowCount;

    public FrameSequence() {
	_updates = new ArrayList<FrameUpdate>();
	_cowCount = 0;
    }

    public void setSize(int w, int h) {
	if (_width != 0 || _height != 0) {
	    if (_width != w || _height != h) {
		System.out.println("WARNING: Trying to reset width/height to different values!");
		System.out.println("  old: " + _width + "x" + _height);
		System.out.println("  new: " + w + "x" + h);
	    }
	}

	_width = w;
	_height = h;
    }

    public void add(FrameUpdate u) {
	if (_updates.size() > 0)
	    u.setPrev(_updates.get(_updates.size() - 1));

	if (_cowCount > MAX_COW_COUNT) {
	    //System.out.println("doing evalFully for frame update at " + u.ts());
	    u = u.evalFully();
	    _cowCount = 0;
	}

	u.setSeq(this);
	_updates.add(u);
	_cowCount++;
    }

    public int size() {
	return _updates.size();
    }

    public int totalPixels() {
	return _width * _height;
    }

    public int width() { return _width; }
    public int height() { return _height; }

    private int getMatchPixels(FrameUpdate u1, FrameUpdate u2,
	FrameUpdate prev, int prevPixels)
    {
	if (prev == null)
	    return u1.getMatchPixels(u2);

	int matches = prevPixels;

	for (int x = 0; x < _width; x++) {
	    for (int y = 0; y < _height; y++) {
		if (u2.contains(x, y)) {
		    int u1p = u1.get(x, y);
		    int u2p = u2.get(x, y);
		    int prevp = prev.get(x, y);

		    if (u1p != u2p && u1p == prevp)
			matches--;
		    if (u1p == u2p && u1p != prevp)
			matches++;
		}
	    }
	}

	return matches;
    }

    public FrameUpdate findClosestMatch(FrameUpdate u, long tsLo, long tsCenter, long tsHi) {
	FrameUpdate bestMatch = null;
	int bestMatchPixels = -1;

	FrameUpdate prev = null;
	int prevPixels = 0;

	for (FrameUpdate i: _updates) {
	    if (i.ts() < tsLo)
		continue;
	    if (i.ts() > tsHi)
		continue;

	    int m = getMatchPixels(u, i, prev, prevPixels);

	    prev = i;
	    prevPixels = m;

	    //System.out.println("comparing with frame at " + i.ts() + ": " + m + " pixel matches");
	    if (i.ts() > tsCenter && m > bestMatchPixels) {
		bestMatch = i;
		bestMatchPixels = m;
	    }

	    if (i.ts() < tsCenter && m >= bestMatchPixels) {
		bestMatch = i;
		bestMatchPixels = m;
	    }
	}

	return bestMatch;
    }

    public Iterator<FrameUpdate> iterator() {
	return _updates.iterator();
    }
}
