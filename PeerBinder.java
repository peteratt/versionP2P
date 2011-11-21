import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that defines the server-side thread for all the peers. It implements
 * the RMI interface of a PeerServer
 * 
 * @author palvare3
 * 
 */
public class PeerBinder extends UnicastRemoteObject implements Runnable,
		PeerServerInterface {

	private static final long serialVersionUID = 1L;

	private Peer peer;

	private boolean timerOn = false;

	private Stack<RegistryItem> queryRegistry = new Stack<RegistryItem>();

	private int previousReqType = -1;
	
	private Logger logger;

	/**
	 * Creates a new PeerBinder object
	 * 
	 * @param id
	 *            of the peer
	 * @param p
	 * @throws RemoteException
	 */
	public PeerBinder(Peer p) throws RemoteException {
		super();
		this.peer = p;
		this.logger = peer.getLogger();
	}

	@Override
	public byte[] obtain(String filename) throws RemoteException {

		String path;
		InputStream is;

		if (peer.isMaster(filename)) {
			path = "./files/";
		} else if (peer.isDownloaded(filename)) {
			path = "./downloads/";
			if (peer.hasFile(filename) != Peer.FILE_VALID) {
				throw new RemoteException("File not up to date");
			}
		} else {
			throw new RemoteException("File not found");
		}
		try {
			is = new FileInputStream(path + filename);

			File file = new File(path + filename);
			// Get the size of the file
			long length = file.length();

			// Create the byte array to hold the data
			byte[] bytes = new byte[(int) length];

			// Read in the bytes
			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length
					&& (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
				offset += numRead;
			}
			// Ensure all the bytes have been read in
			if (offset < bytes.length) {
				throw new IOException("Could not completely read file "
						+ file.getName());
			}

			// Close the input stream and return bytes
			is.close();
			return bytes;
		} catch (FileNotFoundException f) {
			// This is for the case in which a file has been unexpectedly erased
			// from the computer, so it is unregistered in the peer
			peer.removeFile(filename);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}

	@Override
	public void run() {
		// Tries to retrieve the SecurityManager. If not possible, creates a new
		// one
		if (System.getSecurityManager() == null)
			System.setSecurityManager(new RMISecurityManager());
		try {
			// Binds the server to a port
			int port = Peer.BASE_RMI_PORT + peer.getId();
			LocateRegistry.createRegistry(port);
			System.out.println("java RMI registry created.");

			System.out.println("Port: " + port);
			System.out.println("Registration complete with id #" + peer.getId()
					+ ", waiting for connections...");
			Naming.rebind("//localhost:" + port + "/PeerServer" + peer.getId(),
					this);

		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Set<Integer> queryServer(int senderId, String filename, int ttl,
			int requestingId, int requestCode) throws RemoteException {
		// Attempts to reconnect the neighbor
		peer.connectNeighbor(senderId);

		System.out.println();
		if (requestCode == Peer.SEARCH_REQUEST) {
			System.out.println("New query from " + senderId + " requesting "
					+ filename + " for " + requestingId + " with TTL=" + ttl);
			logger.log(Level.INFO, "New query from " + senderId + " requesting "
					+ filename + " for " + requestingId + " with TTL=" + ttl);
		} else {
			System.out.println("New query from " + senderId + " invalidating "
					+ filename + " with masterId=" + requestingId
					+ " with TTL=" + ttl);
		}
		System.out.print("NutPeers > ");

		Set<Integer> sources = new HashSet<Integer>();

		if (peer.hasFile(filename) == Peer.FILE_VALID) {
			if (requestCode == Peer.SEARCH_REQUEST) {
				sources.add(peer.getId());
				peer.incrementTotalRequests(true);
				logger.log(Level.INFO, "Valid request");
			} else if (requestCode == Peer.INVALIDATION_REQUEST) {
				// Invalidate the file
				System.out.println("Invalidating file: " + filename);
				peer.invalidate(filename);
			}
		} else if ((peer.hasFile(filename) == Peer.FILE_TTR_EXPIRED)
				&& (requestCode == Peer.SEARCH_REQUEST)) {
			peer.incrementTotalRequests(false);
			logger.log(Level.INFO, "Invalid request, TTR expired");

			System.out.println("TTR expired in " + peer.getId()
					+ " , updating...");
			if (peer.lazyUpdate(filename)) {
				sources.add(peer.getId());
			}
		} else {
			peer.incrementTotalRequests(false);
			logger.log(Level.INFO, "Invalid request, invalid file");
		}
		ttl--;

		boolean same = false;

		previousReqType = -1;

		// The inclusion of a query registry boosts the performance of a peer,
		// and by extension the whole network's. It compares the last query
		// against a list of the queries the peer has received in the last 15
		// seconds.
		// If the requesting peer and the file are repeated, the peer does not
		// forward again the query to its neighbors, saving a lot of load.
		for (int i = 0; i < queryRegistry.size(); i++) {
			String previousFilename = queryRegistry.get(i).getFilename();
			int previousReq = queryRegistry.get(i).getRequestingId();

			if ((previousFilename.equals(filename))
					&& (previousReq == requestingId)
					&& (previousReqType == requestCode)) {
				System.out.println();
				System.out.println("Same!");
				System.out.print("NutPeers > ");
				same = true;
			}
		}

		// Tries to start the timer for clearing the registry
		if (same) {
			startTimer();
		}

		if ((ttl != 0) && (!same)) {

			previousReqType = requestCode;

			// Sets a capacity limit for the registry, so that the program
			// doesn't run out of memory
			if (queryRegistry.size() > 1000) {
				queryRegistry.clear();
			}

			updateQueryRegistry(filename, requestingId);

			Object[] neighbors = peer.getConnectedNeighbors().keySet()
					.toArray();

			// Queries to neighbors: all except the sender neighbor and the
			// requesting peer in case it is a neighbor
			for (int i = 0; i < neighbors.length; i++) {

				if ((!neighbors[i].equals(senderId))
						&& (!neighbors[i].equals(requestingId))) {
					try {
						PeerServerInterface neighborServer = peer
								.getNeighborInterface((Integer) neighbors[i]);
						if (requestCode == Peer.SEARCH_REQUEST) {
							Set<Integer> destinations = neighborServer
									.queryServer(peer.getId(), filename, ttl,
											requestingId, Peer.SEARCH_REQUEST);
							// This unifies all the sources found so far in a
							// unique
							// Set
							sources.addAll(destinations);
						} else {
							neighborServer.queryServer(peer.getId(), filename,
									ttl, requestingId,
									Peer.INVALIDATION_REQUEST);
						}
					} catch (Exception e) {
						System.out.println();
						System.out.println("The server with id="
								+ (Integer) neighbors[i]
								+ " is not currently available.");
						System.out.print("NutPeers > ");
						peer.disconnectNeighbor((Integer) neighbors[i]);
					}
				}

			}
		}
		if (requestCode == Peer.SEARCH_REQUEST) {
			System.out.println();
			System.out.println("Returning sources to " + senderId);
			System.out.print("NutPeers > ");
		}

		return sources;
	}

	/**
	 * Updates the query registry with a new query
	 * 
	 * @param filename
	 * @param requestingId
	 */
	private synchronized void updateQueryRegistry(String filename,
			int requestingId) {
		RegistryItem item = new RegistryItem(filename, requestingId);
		queryRegistry.push(item);
	}

	/**
	 * Starts the timer if it hasn't yet been initialized
	 */
	private synchronized void startTimer() {
		if (!timerOn) {
			Thread timer = new TimerRestarter(this);
			timer.start();
			timerOn = true;
		}
	}

	@Override
	public synchronized int notifyConnection(int id) throws RemoteException {
		peer.connectNeighbor(id);
		System.out.println();
		System.out.println("Found a neighbor with id: " + id);
		System.out.print("NutPeers > ");
		return PeerMain.MODE;
	}

	/**
	 * Clears the registry every 15 sec
	 */
	public synchronized void timeOutTimer() {
		System.out.println();
		System.out.println("Cleaning query registry");
		System.out.print("NutPeers > ");
		queryRegistry.clear();
		timerOn = false;
	}

	/*--------- start change ----------*/

	@Override
	public int[] checkMetadata(String result) throws RemoteException {
		return peer.getMetadata(result);
	}

	@Override
	public void receiveNeighborsForInvalidation(
			Map<Integer, Boolean> connectedNeighbors) throws RemoteException {
		// Compares each set of neighbors so the invalidation is only sent to
		// neighbors which are not in common
		Object[] thisConnectedNeighbors = peer.getConnectedNeighbors().keySet()
				.toArray();
		Object[] otherConnectedNeighbors = connectedNeighbors.keySet()
				.toArray();

		List<Integer> notCommonDisconnected = new ArrayList<Integer>();

		boolean notCommon = true;
		for (Object i : otherConnectedNeighbors) {
			notCommon = true;
			for (Object j : thisConnectedNeighbors) {
				if (j.equals(i)) {
					notCommon = false;
				}
			}
			if (notCommon)
				notCommonDisconnected.add((Integer) i);
		}

		// Given not common disconnected, invalidates
		for (Integer id : notCommonDisconnected) {
			peer.invalidateFilesFromID(id);
		}

	}

	/*--------- end change ----------*/

}
