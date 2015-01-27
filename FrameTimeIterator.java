import java.util.*;

public class FrameTimeIterator implements Iterator<FrameUpdate>, Iterable<FrameUpdate> {
    private Iterator<FrameUpdate> _i;
    private FrameUpdate _next;

    public FrameTimeIterator(Iterator<FrameUpdate> i) {
	_i = i;
    }

    public FrameTimeIterator(Iterable<FrameUpdate> i) {
	_i = i.iterator();
    }

    public FrameUpdate next() {
	// Pull the next frame update from our one-deep stack.
	FrameUpdate r = _next;
	_next = null;

	// If our stack was empty, pull it off the iterator.
	if (r == null)
	    r = _i.next();

	while (_i.hasNext()) {
	    // Check what's next in the iterator.
	    FrameUpdate n = _i.next();

	    // If we crossed a timestamp boundary, push the extra guy and return.
	    if (n.ts() != r.ts()) {
		_next = n;
		break;
	    }

	    // Otherwise, this is the next guy we're potentially returning.
	    r = n;
	}

	return r;
    }

    public boolean hasNext() {
	if (_next != null)
	    return true;
	return _i.hasNext();
    }

    public void remove() {
	throw new RuntimeException("cannot remove!");
    }

    public Iterator<FrameUpdate> iterator() {
	return this;
    }
}
