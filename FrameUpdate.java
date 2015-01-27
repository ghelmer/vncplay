public class FrameUpdate {
    private int _xbase, _ybase, _width, _height;
    private int _pixels[];
    private long _ts;
    private FrameUpdate _prev;
    private FrameSequence _seq;

    public FrameUpdate(int x, int y, int width, int height, long ts) {
	_xbase = x;
	_ybase = y;
	_width = width;
	_height = height;
	_pixels = new int[width * height];
	_ts = ts;
    }

    public boolean contains(int x, int y) {
	if (x < _xbase)
	    return false;
	if (y < _ybase)
	    return false;
	if (x >= _xbase + _width)
	    return false;
	if (y >= _ybase + _height)
	    return false;

	return true;
    }

    public int getMatchPixels(FrameUpdate u) {
	int matches = 0;
	int width = _seq.width();
	int height = _seq.height();

	for (int x = 0; x < width; x++)
	    for (int y = 0; y < height; y++)
		if (get(x, y) == u.get(x, y))
		    matches++;

	return matches;
    }

    public void setSeq(FrameSequence s) {
	_seq = s;
    }

    public FrameSequence seq() {
	return _seq;
    }

    private int getThis(int x, int y) {
	int xoff = x - _xbase;
	int yoff = y - _ybase;

	return _pixels[xoff + yoff * _width];
    }

    public int get(int x, int y) {
	FrameUpdate f = this;

	while (f != null) {
	    if (x < f._xbase || y < f._ybase || x >= f._xbase + f._width || y >= f._ybase + f._height)
		f = f._prev;
	    else
		return f.getThis(x, y);
	}

	return 0;
    }

    public void set(int x, int y, int value) {
	int xoff = x - _xbase;
	int yoff = y - _ybase;

	_pixels[xoff + yoff * _width] = value;
    }

    public long ts() {
	return _ts;
    }

    public FrameUpdate prev() {
	return _prev;
    }

    public void setPrev(FrameUpdate u) {
	_prev = u;
    }

    private int maxWidth() {
	int m = _xbase + _width;

	if (_prev != null) {
	    int pm = _prev.maxWidth();
	    if (pm > m)
		m = pm;
	}

	return m;
    }

    private int maxHeight() {
	int m = _ybase + _height;

	if (_prev != null) {
	    int pm = _prev.maxHeight();
	    if (pm > m)
		m = pm;
	}

	return m;
    }

    public FrameUpdate evalFully() {
	int w = maxWidth();
	int h = maxHeight();

	FrameUpdate n = new FrameUpdate(0, 0, w, h, ts());
	n.setSeq(_seq);
	for (int x = 0; x < w; x++)
	    for (int y = 0; y < h; y++)
		n.set(x, y, get(x, y));

	return n;
    }
}
