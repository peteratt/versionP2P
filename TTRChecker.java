/*--------- start change ----------*/

/**
 * Class which defines a TTR checker thread. It is basically a trigger which
 * sleeps until TTR milliseconds. When it wakes up, pulls off a metadata refresh
 * in the peer.
 * 
 * @author palvare3
 * 
 */
public class TTRChecker implements Runnable {

	private static int nCounters = 0;
	private static final int MAX_COUNTERS = 5;
	private Peer peer;

	public TTRChecker(Peer p) {
		this.peer = p;
	}

	@Override
	public void run() {
		// Sets a limit to the counters, so as not to overflow memory
		if (nCounters <= MAX_COUNTERS) {
			nCounters++;
			try {
				int ttr = peer.getMinimumTTR();

				System.out.println("Calculated TTR: " + ttr);

				if (ttr > 0) {
					long initialTime = System.currentTimeMillis();
					Thread.sleep(ttr);
					long finalTime = System.currentTimeMillis();

					int waitingTime = (int) (finalTime - initialTime);

					System.out.println("Refreshing downloads after "
							+ waitingTime);
					peer.refreshDownloads(ttr);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

}

/*--------- end change ----------*/

