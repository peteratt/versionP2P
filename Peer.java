import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that defines the properties and state of a Peer as well as client-side
 * methods.
 * 
 * @author palvare3
 * 
 */
public class Peer {

	private static final int TIME_TO_LIVE = 6;

	public static final int BASE_RMI_PORT = 1099;

	/*--------- start change ----------*/

	// Constants for validity
	public static final int FILE_VALID = 0;
	public static final int FILE_TTR_EXPIRED = 1;
	public static final int FILE_INVALID = 2;

	// Codes for types of queries to neighbors
	public static final int SEARCH_REQUEST = 0;
	public static final int INVALIDATION_REQUEST = 1;

	// Constants for fields in downloads table
	private static final int VERSION_FIELD = 0;
	private static final int VALIDITY_FIELD = 1;
	private static final int MASTER_FIELD = 2;
	private static final int TTR_FIELD = 3;

	/*--------- end change ----------*/

	/**
	 * Stores the neighbors as key-value pairs, with the key being the neighbor
	 * id and the value true if it is connected to the P2P network.
	 */
	private Map<Integer, Boolean> connectedNeighbors;

	/*--------- start change ----------*/

	// Files tables:
	// masterFiles has filename as key and version no. as value
	// downloadedFiles has filename as key and its values are comprised in an
	// Integer array.
	// [0] for version
	// [1] for validity
	// [2] for master
	// [3] for TTR
	private Map<String, Integer> masterFiles;
	private Map<String, Integer[]> downloadedFiles;

	private int id;
	private int timeToRefresh;

	// Each modification to metadata sets this timestamp
	private long timestamp = 0;

	private int totalRequests;

	private int invalidRequests;

	private Logger logger;

	/*--------- end change ----------*/

	/**
	 * Peer constructor. Takes the peer id as a parameter, registers files and
	 * looks for neighbors.
	 * 
	 * @param id
	 *            of the peer
	 * @param ttr
	 */
	public Peer(int id, int ttr) {
		this.id = id;
		this.timeToRefresh = ttr;

		connectedNeighbors = new HashMap<Integer, Boolean>();

		/*--------- start change ----------*/
		masterFiles = new HashMap<String, Integer>();
		downloadedFiles = new HashMap<String, Integer[]>();
		/*--------- end change ----------*/

		// For all files present in files directory, register with id
		File dir = new File("./files/");
		File[] fileList = dir.listFiles();

		for (File f : fileList) {
			/*--------- start change ----------*/
			// Does not register .DS_Store, a hidden file in Mac OS X file
			// system
			if (!f.getName().equals(".DS_Store")) {
				masterFiles.put(f.getName(), 0);
				System.out.println("Registered " + f.getName());
			}
			/*--------- end change ----------*/
		}

		// For all files present in downloads directory, register with id
		dir = new File("./downloads/");
		fileList = dir.listFiles();

		/*--------- start change ----------*/

		for (File f : fileList) {
			if (!f.getName().equals(".DS_Store")) {
				// To simplify, invalidate all downloaded files
				// -1 for version number at the beginning
				Integer master = 0; // 0 as default, lost contact with master
				Integer[] fileProperties = { -1, FILE_INVALID, master, 0 };
				downloadedFiles.put(f.getName(), fileProperties);
				System.out.println("Registered downloaded " + f.getName());
			}
		}

		/*--------- end change ----------*/

		findNeighbors();
		
		try {
		    // Create an appending file handler
		    boolean append = true;
		    FileHandler handler = new FileHandler("peer" + id + ".log", append);

		    // Add to the desired logger
		    logger = Logger.getLogger("Peers");
		    logger.addHandler(handler);
		    
		} catch (IOException e) {
		}
	}

	/**
	 * Looks for neighbors within the network
	 */
	private void findNeighbors() {
		// Read peer.properties to get predefined neighbors
		Properties properties = new Properties();

		try {
			properties.load(new FileInputStream("peer.properties"));
			String[] s = properties.getProperty("neighbors").split(",");

			for (String idN : s) {
				int neighborId = Integer.parseInt(idN);
				int port = BASE_RMI_PORT + neighborId;
				System.out.println("Searching for neighbor in port: " + port);
				try {
					PeerServerInterface neighborServer = null;
					String namePeerServer = "//localhost:" + port
							+ "/PeerServer" + neighborId;
					System.out.println(namePeerServer);

					neighborServer = (PeerServerInterface) Naming
							.lookup(namePeerServer);
					connectedNeighbors.put(neighborId, true);

					/*--------- start change ----------*/
					// Notify neighborhood and retrieve mode
					// If mode of neighbor is not compatible, shut down
					int neighborMode = neighborServer.notifyConnection(id);
					if (PeerMain.MODE != neighborMode) {
						System.out.println("FATAL ERROR, MODES NOT COMPATIBLE");
						System.exit(1);
					}
					/*--------- end change ----------*/

					System.out.println("Found a neighbor with id: "
							+ neighborId);

					/*--------- start change ----------*/
					// If mode is push, announce the neighbors to unregister the
					// version they have.
					// This is used when a peer shuts down accidentally
					if (PeerMain.MODE == PeerMain.MODE_PUSH) {
						unregisterMasterFiles(neighborId);
					}
					/*--------- end change ----------*/

				} catch (Exception e) {
					connectedNeighbors.put(neighborId, false);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method called to start a search request for a file in the network
	 * 
	 * @param filename
	 *            requested
	 * @return The set of neighbor ids that have the file
	 */
	/**
	 * Method called to start a search request for a file in the network
	 * 
	 * @param filename
	 *            requested
	 * @return The set of neighbor ids that have the file
	 */
	public Set<Integer> search(String filename) {

		Set<Integer> sources = new HashSet<Integer>();

		// Sends the request to all neighbors
		System.out.println("Searching on the network for... " + filename);
		Object[] neighbors = connectedNeighbors.keySet().toArray();

		for (int i = 0; i < connectedNeighbors.size(); i++) {
			if (connectedNeighbors.get((Integer) neighbors[i])) {
				try {
					PeerServerInterface neighborServer = getNeighborInterface((Integer) neighbors[i]);
					System.out.println("Querying server "
							+ (Integer) neighbors[i]);
					Set<Integer> destinations = neighborServer.queryServer(id,
							filename, TIME_TO_LIVE, id, SEARCH_REQUEST);

					// Merges the current sources with the result of the last
					// query
					sources.addAll(destinations);
				} catch (Exception e) {
					disconnectNeighbor((Integer) neighbors[i]);
				}
			}
		}

		return sources;
	}

	/**
	 * @return Peer ID
	 */
	public int getId() {
		return this.id;
	}

	/*--------- start change ----------*/
	/**
	 * @return This peer's Time To Refresh as found in config file
	 */
	public int getTTR() {
		return this.timeToRefresh;
	}

	/**
	 * @param filename
	 * @return FILE_VALID (0) if the file exists in this peer and is valid <br>
	 *         FILE_TTR_EXPIRED (1) if the file exists in this peer but TTR has
	 *         expired <br>
	 *         FILE_INVALID (2) in other cases
	 */
	public int hasFile(String filename) {
		Object[] filenames = masterFiles.keySet().toArray();
		// Check for master files
		for (Object f : filenames) {
			if (filename.equals((String) f))
				return FILE_VALID;
		}

		// Check for downloaded files
		filenames = downloadedFiles.keySet().toArray();
		for (Object f : filenames) {
			if (filename.equals((String) f)
					&& (downloadedFiles.get(f)[VALIDITY_FIELD] == FILE_VALID)) {
				return FILE_VALID;
			} else if (filename.equals((String) f)
					&& (downloadedFiles.get(f)[VALIDITY_FIELD] == FILE_TTR_EXPIRED)) {
				return FILE_TTR_EXPIRED;
			}
		}
		return FILE_INVALID;
	}

	/**
	 * Adds a file to the list of downloaded files
	 * 
	 * @param filename
	 */
	public void addFile(String filename, int[] metadata) {
		Integer[] fileProperties = new Integer[4];
		fileProperties[VERSION_FIELD] = metadata[0];
		fileProperties[VALIDITY_FIELD] = FILE_VALID;
		fileProperties[MASTER_FIELD] = metadata[1];
		fileProperties[TTR_FIELD] = metadata[2];

		System.out.println("Adding metadata of " + filename + ": "
				+ metadata.toString());

		// Insert metadata
		downloadedFiles.put(filename, fileProperties);

		// Updates the timestamp for the first modification of metadata at init
		if (timestamp == 0) {
			timestamp = System.currentTimeMillis();
			System.out.println("Timestamp set to " + timestamp);
		}
	}

	/**
	 * Removes a file from the list of master files
	 * 
	 * @param filename
	 */
	public void removeFile(String filename) {
		masterFiles.remove(filename);
	}

	/*--------- end change ----------*/

	/**
	 * Sets a neighbor as connected
	 * 
	 * @param neighborId
	 */
	public void connectNeighbor(int neighborId) {
		connectedNeighbors.put(neighborId, true);
	}

	/**
	 * Sets a neighbor as disconnected
	 * 
	 * @param neighborId
	 */
	public void disconnectNeighbor(int neighborId) {
		connectedNeighbors.put(neighborId, false);
	}

	/**
	 * @return The list of connected neighbors
	 */
	public Map<Integer, Boolean> getConnectedNeighbors() {
		return this.connectedNeighbors;
	}

	/*--------- start change ----------*/
	/**
	 * Retrieves the current stored version of a file
	 * 
	 * @param result
	 *            Name of the file
	 * @return Version of file
	 */
	public int getVersion(String result) {
		if (masterFiles.get(result) != null) {
			return masterFiles.get(result);
		}

		return downloadedFiles.get(result)[0];
	}

	/**
	 * Increments the version of a file, only in master
	 * 
	 * @param filename
	 *            to modify
	 */
	public void incrementVersion(String filename) {
		int version = masterFiles.get(filename);
		version++;

		masterFiles.put(filename, version);
	}

	/**
	 * Invalidates a file
	 * 
	 * @param filename
	 *            to invalidate
	 */
	public void invalidate(String filename) {
		System.out.println("Invalidating downloaded file: " + filename);
		downloadedFiles.get(filename)[VALIDITY_FIELD] = FILE_INVALID;
	}

	/**
	 * Sends an invalidation request for all files present in the peer to a
	 * neighbor
	 * 
	 * @param neighborId
	 *            Id of the neighbor
	 */
	public void unregisterMasterFiles(int neighborId) {
		Object[] files = masterFiles.keySet().toArray();

		try {
			if (connectedNeighbors.get(neighborId)) {
				PeerServerInterface neighborServer = getNeighborInterface(neighborId);

				for (Object filename : files) {
					neighborServer.queryServer(id, (String) filename,
							TIME_TO_LIVE, id, INVALIDATION_REQUEST);
				}
				// Sends the neighbors of this peer to the neighbor, so it can
				// handle invalidations
				// in case two parts of the network become disconnected when
				// this peer disconnects
				neighborServer
						.receiveNeighborsForInvalidation(connectedNeighbors);
			}
		} catch (Exception e) {
			disconnectNeighbor(id);
		}
	}

	/**
	 * @param neighborId
	 *            Id of the neighbor
	 * @return The neighbor PeerServerInterface stub
	 * 
	 * @throws MalformedURLException
	 * @throws RemoteException
	 * @throws NotBoundException
	 */
	public PeerServerInterface getNeighborInterface(int neighborId)
			throws MalformedURLException, RemoteException, NotBoundException {

		PeerServerInterface neighborServer = null;
		if (connectedNeighbors.get(neighborId)) {
			int port = BASE_RMI_PORT + neighborId;
			String namePeerServer = "//localhost:" + port + "/PeerServer"
					+ neighborId;

			neighborServer = (PeerServerInterface) Naming
					.lookup(namePeerServer);
		}
		return neighborServer;
	}

	/**
	 * Modifies a master file and handles version control depending on the peer
	 * mode.
	 * 
	 * @param filename
	 *            to modify
	 */
	public void modifyMasterFile(String filename) {
		if (isMaster(filename)) {
			try {
				// Modification of file
				RandomAccessFile raf = new RandomAccessFile("./files/"
						+ filename, "rw");
				raf.seek(raf.length());
				raf.write(FileModifier.MODIFY_STRING.getBytes());
				raf.close();

				incrementVersion(filename);
				System.out.println("Version of " + filename + " modified to: "
						+ getVersion(filename));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// If the mode is "push", broadcast the modification to the network
			if (PeerMain.MODE == PeerMain.MODE_PUSH) {
				connectedNeighbors.keySet().toArray();
				for (Object neighborId : connectedNeighbors.keySet().toArray()) {
					if (connectedNeighbors.get((Integer) neighborId)) {
						try {
							PeerServerInterface neighborServer = getNeighborInterface((Integer) neighborId);
							neighborServer.queryServer(id, filename,
									TIME_TO_LIVE, id, INVALIDATION_REQUEST);
						} catch (Exception e) {
							disconnectNeighbor((Integer) neighborId);
						}
					}
				}
			}
		} else {
			System.out.println("Can't modify the file");
		}
	}

	/**
	 * Checks if the file belongs to this peer
	 * 
	 * @param filename
	 * @return true if the filename is in this peer and is master copy
	 */
	public boolean isMaster(String filename) {
		if (masterFiles.get(filename) != null) {
			return true;
		}

		return false;
	}

	/**
	 * Checks if the file has been downloaded in this peer
	 * 
	 * @param filename
	 * @return true if the filename is in this peer and is a downloaded copy
	 */
	public boolean isDownloaded(String filename) {
		if (downloadedFiles.get(filename) != null) {
			return true;
		}

		return false;
	}

	/**
	 * Invalidates all files which master copy is in a certain id
	 * 
	 * @param id
	 */
	public void invalidateFilesFromID(Integer id) {
		Object[] downFilesArray = downloadedFiles.keySet().toArray();

		for (Object filename : downFilesArray) {
			if (downloadedFiles.get((String) filename)[MASTER_FIELD].equals(id)) {
				downloadedFiles.get((String) filename)[VALIDITY_FIELD] = FILE_INVALID;
			}
		}

	}

	/**
	 * Retrieves the metadata of a file
	 * 
	 * @param result
	 *            Filename to analyze
	 * @return An array with the following metadata: <br>
	 *         metadata[0]: Version metadata[1]: Master id metadata[2]: Current
	 *         TTR
	 */
	public int[] getMetadata(String result) {

		int[] metadata = new int[3];
		if (masterFiles.get(result) != null) {
			metadata[0] = masterFiles.get(result);
			metadata[1] = id;
			// If file is master copy, return TTR of the peer
			metadata[2] = timeToRefresh;
			return metadata;
		}

		if (downloadedFiles.get(result) != null) {
			metadata[0] = downloadedFiles.get(result)[VERSION_FIELD];
			metadata[1] = downloadedFiles.get(result)[MASTER_FIELD];
			// If file is downloaded, return TTR corrected by actual waiting
			// time before new refresh
			long currentTime = System.currentTimeMillis();
			long timeDiff = currentTime - timestamp;
			System.out.println("Time difference: " + timeDiff);
			metadata[2] = downloadedFiles.get(result)[TTR_FIELD]
					- (int) timeDiff;
			return metadata;
		}
		return null;
	}

	/**
	 * @return The minimum TTR present in the metadata of downloaded files, so a
	 *         new timer to check TTRs can be set with the minimum TTR.
	 */
	public int getMinimumTTR() {
		Object[] filesDown = downloadedFiles.keySet().toArray();

		int minTTR = Integer.MAX_VALUE;

		for (Object filename : filesDown) {
			if ((downloadedFiles.get((String) filename)[3] > 0)
					&& (downloadedFiles.get((String) filename)[3] < minTTR)) {
				minTTR = downloadedFiles.get((String) filename)[3];
			}
		}
		// If there aren't valid TTRs in the metadata, sets minTTR to a negative
		// value in order not to initialize any timers.
		if (minTTR == Integer.MAX_VALUE) {
			minTTR = -1;
		}
		System.out.println("New TTR=" + minTTR);
		return minTTR;
	}

	/**
	 * Refreshes metadata of downloaded files
	 * 
	 * @param waitingTime
	 *            Time passed between refreshes
	 */
	public synchronized void refreshDownloads(int waitingTime) {
		Object[] filesDown = downloadedFiles.keySet().toArray();

		for (Object filename : filesDown) {
			// Updates TTRs by subtracting the time between last refresh and
			// current
			downloadedFiles.get((String) filename)[3] -= waitingTime;

			if (downloadedFiles.get((String) filename)[3] <= 0) {
				System.out.println("TTR expired!");
				// Sets all expirations as TTR=0
				downloadedFiles.get((String) filename)[3] = 0;

				if (downloadedFiles.get((String) filename)[VALIDITY_FIELD] != FILE_INVALID) {
					downloadedFiles.get((String) filename)[VALIDITY_FIELD] = FILE_TTR_EXPIRED;
				}
			}
		}

		// Sets new timestamp and triggers a new TTRChecker
		timestamp = System.currentTimeMillis();
		System.out.println("New timestamp set at " + timestamp);

		TTRChecker checker = new TTRChecker(this);
		Thread checkerThread = new Thread(checker);
		checkerThread.start();
	}

	/**
	 * Performs a lazy update in pull mode.
	 * 
	 * @param filename
	 * @return if the update has been pulled out correctly
	 */
	public boolean lazyUpdate(String filename) {
		// Lazy update
		int[] metadata = getMetadata(filename);
		try {
			System.out.println("Querying " + metadata[1] + " for lazy update");
			int port = BASE_RMI_PORT + metadata[1];
			String namePeerServer = "//localhost:" + port + "/PeerServer"
					+ metadata[1];

			PeerServerInterface master = (PeerServerInterface) Naming
					.lookup(namePeerServer);

			int[] newMetadata = master.checkMetadata(filename);

			if (getVersion(filename) != newMetadata[0]) {
				master.obtain(filename);
				System.out
						.println("Version changed, downloading from master...");
			} else {
				// Just restablishes TTR of the file to its initial value
				System.out.println("Version unchanged, refreshing TTR.");
			}

			updateMetadata(filename, newMetadata);

			// Starts a new TTRChecker to trigger refreshes of the file
			TTRChecker checker = new TTRChecker(this);
			Thread checkerThread = new Thread(checker);
			checkerThread.start();

			return true;

		} catch (Exception e) {
			System.out.println("Lazy update not possible, master unreachable.");
			e.printStackTrace();
			return false;
		}

	}

	/**
	 * Updates the metadata of a file in downloaded files
	 * 
	 * @param filename
	 * @param newMetadata
	 */
	private synchronized void updateMetadata(String filename, int[] newMetadata) {
		Integer[] m = { newMetadata[0], FILE_VALID, newMetadata[1],
				newMetadata[2] };
		downloadedFiles.put(filename, m);
	}

	/**
	 * Increments the number of search queries to the server. If a query is
	 * failed, increments also the number of failed requests to retrieve
	 * statistics.
	 * 
	 * @param isValid
	 */
	public void incrementTotalRequests(boolean isValid) {
		// If the request is valid, just increment total number
		totalRequests++;

		if (!isValid) {
			invalidRequests++;
		}
	}

	/**
	 * Prints statistics at Peer's ending of execution
	 */
	public void printStatistics() {

		System.out.println("FINAL STATS");
		System.out.println("Total requests: " + totalRequests);
		System.out.println("Invalid requests: " + invalidRequests);
		double percentage = 0;
		
		if (totalRequests > 0) {
			percentage = ((double)invalidRequests / (double)totalRequests) * 100;
		}
		
		System.out.println("Percentage of invalid requests: " + percentage
				+ "%");
		logger.log(Level.INFO, "Total requests: " + totalRequests);
		logger.log(Level.INFO, "Invalid requests: " + invalidRequests);
		logger.log(Level.INFO, "Percentage of invalid requests: " + percentage);
		
		logger.getHandlers()[0].flush();
		logger.getHandlers()[0].close();
	}

	public Logger getLogger() {
		return logger;
	}

	/*--------- end change ----------*/

}
