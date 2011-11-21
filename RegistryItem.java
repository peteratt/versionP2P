/**
 * Defines an item for the query registry of the server side of the peer.
 * 
 * @author palvare3
 * 
 */
public class RegistryItem {

	private String filename;
	private int requestingId;

	public RegistryItem(String filename, int requestingId) {
		this.filename = filename;
		this.requestingId = requestingId;
	}

	public String getFilename() {
		return filename;
	}

	public int getRequestingId() {
		return requestingId;
	}

}