import java.util.*;

public class SyncSequence {
    private ArrayList<Long> _syncTimes;

    public SyncSequence() {
	_syncTimes = new ArrayList<Long>();
    }

    public void addSyncTime(long ts) {
	_syncTimes.add(ts);
    }

    public int size() {
	return _syncTimes.size();
    }

    // return the last sync point that happened before ts
    public int syncPoint(long ts) {
	int sz = _syncTimes.size();

	for (int i = 0; i < sz; i++)
	    if (_syncTimes.get(i) > ts)
		return i - 1;

	return sz - 1;
    }

    // return the time of this sync point
    public long syncTime(int num) {
	int sz = _syncTimes.size();

	if (num < 0)
	    num = 0;
	if (num >= sz)
	    num = sz - 1;

	return _syncTimes.get(num);
    }
}
