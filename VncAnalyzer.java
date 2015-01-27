import java.io.*;

public class VncAnalyzer {
    public static final int INTERESTING_PCT_DIFF = 2;

    private static FrameSequence getSeq(String filename) throws IOException {
	File f = new File(filename);
	FrameSequencer fsr = new FrameSequencer(f);
	fsr.create();
	FrameSequence fs = fsr.getFrameSequence();

	System.out.println("file " + filename + " -> " +
			   fs.size() + " updates");

	return fs;
    }

    private static SyncSequence getSync(String filename, int i) throws IOException {
	File f = new File(filename);
	FileReader fr = new FileReader(f);
	BufferedReader br = new BufferedReader(fr);
	SyncSequence ss = new SyncSequence();

	ss.addSyncTime(0);

	long totalExtra = 0;
	boolean waitingForSync = false;
	while (true) {
	    String line = br.readLine();
	    if (line == null)
		break;
	    line.trim();
	    String parts[] = line.split(" ");

	    if (line.startsWith("Waiting for sync"))
		waitingForSync = true;
	    if (line.startsWith("Sync ok at") && waitingForSync) {
		long ts = Long.parseLong(parts[3]);
		ss.addSyncTime(ts);
		waitingForSync = false;
	    }
	    if (line.startsWith("== sending mouse click event at"))
		System.out.println("input_ts: " + i + ": " + parts[6]);
	    if (line.startsWith("== sending keyboard event at"))
		System.out.println("input_ts: " + i + ": " + parts[5]);
	}

	ss.addSyncTime(Integer.MAX_VALUE);

	System.out.println("sync file " + filename + " -> " +
			   ss.size() + " sync points");

	return ss;
    }

    public static void main(String[] args) throws IOException {
	if (args.length < 4 || (args.length % 2 != 0)) {
	    System.out.println("Usage: VncAnalyzer log0 run-one-expt0.out log1 run-one-expt1.out ...");
	    System.exit(1);
	}

	int numSeqs = args.length / 2;
	SyncSequence sync[] = new SyncSequence[numSeqs];
	FrameSequence seq[] = new FrameSequence[numSeqs];

	int syncSize = -1;
	for (int i = 0; i < numSeqs; i++) {
	    System.out.println("Sync log " + i + ": " + args[2*i + 1]);
	    sync[i] = getSync(args[2*i + 1], i);
	    if (syncSize == -1)
		syncSize = sync[i].size();
	    if (syncSize != sync[i].size()) {
		System.out.println("Sync point count mismatch: " + sync[i].size() + " != " + syncSize);
		System.exit(1);
	    }
	}

	for (int i = 0; i < numSeqs; i++) {
	    System.out.println("Frame sequence " + i + ": " + args[2*i]);
	    seq[i] = getSeq(args[2*i]);
	}

	FrameUpdate last = null;
	for (FrameUpdate u: new FrameTimeIterator(seq[0])) {
	    boolean analyze = false;

	    if (last == null) {
		analyze = true;
	    } else {
		int matchPixels = u.getMatchPixels(last);
		int totalPixels = u.seq().totalPixels();
		int matchPercent = (100 * matchPixels) / totalPixels;

		System.out.println("-- frame " + u.ts() + " is " + matchPercent + "% similar to " + last.ts());
		if (matchPercent < (100 - INTERESTING_PCT_DIFF))
		    analyze = true;
	    }

	    if (analyze) {
		int syncLo = sync[0].syncPoint(u.ts() - 500);
		int syncCtr = sync[0].syncPoint(u.ts());
		int syncHi = sync[0].syncPoint(u.ts() + 500);

		System.out.println("== sync intervals: " + syncLo + "-"
							 + syncCtr + "-"
							 + syncHi);

		for (int i = 0; i < numSeqs; i++) {
		    long syncTimeLo = sync[i].syncTime(syncLo - 2) - 500;
		    long syncTimeCtr = sync[i].syncTime(syncCtr);
		    long syncTimeHi = sync[i].syncTime(syncHi + 2) + 500;

		    System.out.println("== search range for " + i + ": " +
				       syncTimeLo + "-" + syncTimeCtr + "-" + syncTimeHi);

		    FrameUpdate m = seq[i].findClosestMatch(u, syncTimeLo, syncTimeCtr, syncTimeHi);
		    if (m == null) {
			System.out.println("== what?  findClosestMatch returned null");
		    } else {
			int pixelMatches = u.getMatchPixels(m);
			int pixelTotal = u.seq().totalPixels();
			System.out.println("== " + u.ts() + "@0" + " matches with " + m.ts() + "@" + i +
					   " (" + pixelMatches + "/" + pixelTotal + ")");
		    }
		}

		last = u;
	    }
	}

	System.exit(0);
    }
}
