/*--------- start change ----------*/

import java.io.File;
import java.util.Random;

/**
 * Automatic file modifier thread used for simulation purposes.
 * 
 * @author palvare3
 * 
 */
public class FileModifier implements Runnable {

	private Peer peer;

	/**
	 * Inverse of the rate in an exponential distribution. Usually is equal to
	 * the TTR of the peer.
	 */
	private int lambda;

	public static final String MODIFY_STRING = "MODIFIED\n";

	public FileModifier(Peer p, int lambda) {
		this.peer = p;
		this.lambda = lambda;
	}

	@Override
	public void run() {

		System.out.println("File Modifier starting");

		Random gen = new Random();

		// For all files present in files directory
		File dir = new File("./files/");
		File[] fileList = dir.listFiles();

		int i = 0;

		while (true) {
			double rand = gen.nextDouble();

			// Wait time between modifications is an exponentially distributed
			// random variable
			// Each file is modified in average lambda milliseconds
			double waitTime = -Math.log(1 - rand) * lambda;

			System.out.println("Autosleeping for " + waitTime);

			try {
				Thread.sleep(Math.round(waitTime));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			System.out.println("AutoModifying " + "test" + peer.getId() + ".txt");
			peer.modifyMasterFile("test" + peer.getId() + ".txt");
			i++;

			if (i == fileList.length)
				i = 0;
		}

	}

}

/*--------- end change ----------*/
