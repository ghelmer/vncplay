import java.util.*;
import java.awt.*;

public class ScreenUpdateWatcher {
    private boolean _givenUp;
    private int _maxUpdateCount;
    private Rectangle _watchBounds;
    private java.util.List<Rectangle> _updates;

    public ScreenUpdateWatcher(Rectangle bounds, int maxCount) {
	_givenUp = false;
	_watchBounds = bounds;
	_maxUpdateCount = maxCount;
	_updates = new ArrayList<Rectangle>();
    }

    public void markUpdated(Rectangle r) {
	if (_givenUp)
	    return;
	if (!_watchBounds.intersects(r))
	    return;

	_updates.add(r);
	if (_updates.size() > _maxUpdateCount)
	    _givenUp = true;
    }

    public boolean isUnchanged(Rectangle r) {
	if (_givenUp)
	    return false;
	if (!_watchBounds.contains(r))
	    return false;

	for (Rectangle update: _updates)
	    if (update.intersects(r))
		return false;

	return true;
    }
}
