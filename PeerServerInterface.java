import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

/**
 * Defines the server-side interface of a peer offered to all neighbors
 * 
 * @author palvare3
 */
public interface PeerServerInterface extends Remote {

	/**
	 * Lets a client obtain a certain file from the peer. It is implemented
	 * using a buffered read, so as not to block the program when accessing big
	 * files.
	 * 
	 * @param filename
	 *            Name of the desired file
	 * 
	 * @return A byte array with the data
	 * 
	 * @throws RemoteException
	 *             if the server cannot be reached
	 */
	public byte[] obtain(String filename) throws RemoteException;

	/**
	 * This is used for notifying the peer that a neighbor has connected
	 * 
	 * @param id
	 *            of the neighbor
	 * @return true if the notification was handled correctly
	 * @throws RemoteException
	 */
	public int notifyConnection(int id) throws RemoteException;

	/**
	 * The base method for propagating a search request over the distributed P2P
	 * network.
	 * 
	 * @param senderId
	 *            The neighbor which directly makes the query
	 * @param filename
	 * @param ttl
	 *            Time to Live
	 * @param requestingId
	 *            Peer that made the original request
	 * 
	 * @return A set with all ids which have the file
	 * @throws RemoteException
	 */
	public Set<Integer> queryServer(int senderId, String filename, int ttl,
			int requestingId, int requestCode) throws RemoteException;

	/**
	 * Searches the metadata of a file in the receiving peer.
	 * 
	 * @param result
	 *            File name
	 * @return See Peer.getMetadata()
	 * @throws RemoteException
	 */
	public int[] checkMetadata(String result) throws RemoteException;

	/**
	 * Receives the caller's neighbors in an invalidation broadcasted by a push
	 * peer which is disconnecting
	 * 
	 * @param connectedNeighbors
	 *            of the caller peer
	 * @throws RemoteException
	 */
	public void receiveNeighborsForInvalidation(
			Map<Integer, Boolean> connectedNeighbors) throws RemoteException;

}
