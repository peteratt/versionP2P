import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

/**
 * Class that defines the user interface of a Peer. A Peer is a client and a
 * server at the same time. This class is formed only by a main method which
 * deploys a command prompt as well as registering the peer's information and
 * starting the server thread.
 * 
 * @author palvare3
 * 
 */
public class PeerMain {

	/*--------- start change ----------*/
	// Default mode: Push
	static int MODE = 0;

	static boolean MODE_AUTOMATIC = false;

	private static int iterations = 1000;

	public static final int MODE_PUSH = 0;
	public static final int MODE_PULL = 1;

	/*--------- end change ----------*/

	public static void main(String argv[]) {

		/*--------- start change ----------*/
		// Parses the arguments to select mode and simulation options
		if ((argv.length > 0) && (argv[0].equals("help"))) {
			System.out.println("NutPeers help: PeerMain [mode] [auto_type] [lambda/ttr] [iterations]");
			System.out.println("mode: push, pull");
			System.out.println("auto_type: auto, modify");
			System.exit(0);
		}
		if ((argv.length > 0) && (argv[0].equals("pull"))) {
			MODE = MODE_PULL;
			System.out.println("SYSTEM MODE SET TO PULL");
		} else if ((argv.length > 0) && (argv[0].equals("push"))) {
			MODE = MODE_PUSH;
			System.out.println("SYSTEM MODE SET TO PUSH");
		} else {
			System.out.println("SYSTEM MODE SET TO PUSH");
		}
		/*--------- end change ----------*/

		if (System.getSecurityManager() == null)
			System.setSecurityManager(new RMISecurityManager());
		try {

			// Gets data from the peer.properties file
			Properties properties = new Properties();
			int id = 0;

			/*--------- start change ----------*/
			// Default TTR= 6 seconds
			int ttr = 6000;

			int lambda = 0;

			properties.load(new FileInputStream("peer.properties"));
			id = Integer.parseInt(properties.getProperty("peer_id"));
			ttr = Integer.parseInt(properties.getProperty("TTR"));

			// Creates a new Peer object
			Peer thisPeer = new Peer(id, ttr);

			// Modifier mode, starts the FileModifier thread
			if ((argv.length > 1) && (argv[1].equals("modifier"))) {
				if (argv.length > 2) {
					ttr = Integer.parseInt(argv[2]);
					System.out.println("TTR changed to " + ttr);
				}
				FileModifier fm = new FileModifier(thisPeer, ttr);
				Thread fmThread = new Thread(fm);
				fmThread.start();
				
			}

			// Automatic requesting mode
			if ((argv.length > 1) && (argv[1].equals("auto"))) {
				MODE_AUTOMATIC = true;
				System.out.println("ENTERING AUTOMATIC MODE");
				// Default lambda
				lambda = ttr;
				if (argv.length > 2) {
					lambda = Integer.parseInt(argv[2]);
				}
				if (argv.length > 3) {
					iterations = Integer.parseInt(argv[3]);
				}
				
			}
			/*--------- end change ----------*/

			// Binds the server side of the peer to a RMI port
			PeerBinder binder = new PeerBinder(thisPeer);
			Thread binderThread = new Thread(binder);
			binderThread.start();

			/*--------- start change ----------*/
			if (MODE == MODE_PULL) {
				TTRChecker checker = new TTRChecker(thisPeer);
				Thread checkerThread = new Thread(checker);
				checkerThread.start();
			}

			if (MODE_AUTOMATIC) {
				automaticPeer(thisPeer, lambda);
			} else {
				// Launches the command prompt
				System.out.println("Launching command line...");
				commandPrompt(thisPeer);
			}

			Object[] list = thisPeer.getConnectedNeighbors().keySet().toArray();

			if (MODE == MODE_PUSH) {
				for (Object neighborId : list) {
					System.out.println("Unregistering master files in "
							+ (Integer) neighborId);
					thisPeer.unregisterMasterFiles((Integer) neighborId);
				}
			}
			thisPeer.printStatistics();

			System.exit(0);
			/*--------- end change ----------*/

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static void automaticPeer(Peer thisPeer, int lambda)
			throws Exception {
		int i = 1;
		int nIter = 0;
		Random r = new Random();
		PeerServerInterface dataSource;

		byte[] data = null;
		int[] metadata = new int[2];

		while (nIter < iterations ) {
			System.out.println("Iteration number " + nIter);
			// Queries the servers for the desired file
			String result = "test" + i + ".txt";
			Set<Integer> sources = thisPeer.search(result);
			if (sources.size() > 0) {
				int sourceId = (Integer) sources.toArray()[r.nextInt(sources
						.size())];

				int port = 1099 + sourceId;
				String namePeerServer = "//localhost:" + port + "/PeerServer"
						+ sourceId;

				try {
					dataSource = (PeerServerInterface) Naming
							.lookup(namePeerServer);
					data = dataSource.obtain(result);
					metadata = dataSource.checkMetadata(result);
				} catch (Exception e) {
					System.out.println(e.getMessage());
					System.out
							.println("The server is not currently available. Please try again");
				}

				writeFile(result, data, metadata, thisPeer);

				System.out.println("Succesfully copied " + result
						+ " from PeerServer" + sourceId);
			}

			// Randomly selects new query
			i = 1 + r.nextInt(10);
			nIter++;

			double rand = r.nextDouble();

			double waitTime = -Math.log(1 - rand) * lambda;

			System.out.println("Autosleeping for " + waitTime);

			Thread.sleep(Math.round(waitTime));
		}
	}

	private static void commandPrompt(Peer thisPeer) throws Exception {

		// Data input from keyboard
		Scanner input = new Scanner(System.in); // Decl. & init. a Scanner.
		String command = "";
		CommandProcessor cp = new CommandProcessor(thisPeer);

		do {

			System.out.print("NutPeers > "); // Asks for commands
			command = input.nextLine(); // Get what the user types.

			// Process command
			String result = cp.process(command);

			if (result != null) {
				// Queries the servers for the desired file
				Set<Integer> sources = thisPeer.search(result);

				Object[] sourcesArray = sources.toArray();

				String source = "";
				boolean validQuery = false;
				PeerServerInterface dataSource;
				String namePeerServer = "";
				byte[] data = null;
				int[] metadata = new int[2];

				if (sourcesArray.length < 1) {
					System.out
							.println("File not available in the network. Please try again");
					validQuery = true;
				}

				while (!validQuery) { // This condition avoids invalid
										// commands and keeps the prompt in
										// the
					// "choosing peer to download" state

					// Presents the options for downloading
					System.out
							.print("Choose the server you want to download from: [");
					for (int i = 0; i < sources.size(); i++) {
						if (i == sources.size() - 1) {
							System.out.print((Integer) sourcesArray[i]);
						} else {
							System.out.print((Integer) sourcesArray[i] + ", ");
						}
					}
					System.out.print("] > ");
					source = input.nextLine();

					// If a user enters "cancel" at this point, the download
					// will be cancelled
					if (!source.equals("cancel")) {
						for (int j = 0; j < sources.size(); j++) {
							Integer s = (Integer) sourcesArray[j];
							if (source.equals(s.toString())) {
								int port = 1099 + s;
								namePeerServer = "//localhost:" + port
										+ "/PeerServer" + source;
								// This is put here so if other peers have
								// not correctly unregistered,
								// the program can treat the error and
								// inform the indexing server
								try {
									dataSource = (PeerServerInterface) Naming
											.lookup(namePeerServer);
									data = dataSource.obtain(result);
									metadata = dataSource.checkMetadata(result);
									validQuery = true;
								} catch (Exception e) {
									System.out.println(e.getMessage());
									System.out
											.println("The server is not currently available. Please try again");
								}
							}
						}
					} else {
						System.out.println("Operation cancelled");
						validQuery = true;
					}

				}

				writeFile(result, data, metadata, thisPeer);

				System.out.println("Succesfully copied " + result
						+ " from PeerServer" + source);
			}

		} while (!command.equals("exit"));
	}

	private static void writeFile(String filename, byte[] data, int[] metadata,
			Peer thisPeer) throws Exception {
		// Writing to a local file
		if (data != null) {
			// Erase the file in case it exists
			File f = new File("downloads/" + filename);
			f.delete();

			FileOutputStream fos = new FileOutputStream("downloads/" + filename);
			fos.write(data);
			fos.close();

			// Finally, register the new file in the peer's registry and start
			// the checker
			// if mode is pull
			thisPeer.addFile(filename, metadata);

			if (MODE == MODE_PULL) {
				TTRChecker checker = new TTRChecker(thisPeer);
				Thread checkerThread = new Thread(checker);
				checkerThread.start();
			}
		}
	}

}
