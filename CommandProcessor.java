/**
 * Class that defines the command processor of the command prompt.
 * 
 * @author palvare3
 */
public class CommandProcessor {

	private static final String HELP = "Peer2PeerRMI Help: \n obtain [filename] \n exit";

	private Peer peer;

	public CommandProcessor(Peer p) {
		this.peer = p;
	}

	/**
	 * Processes the commands given by the user.
	 * 
	 * @param command
	 * @return A filename if the command is obtain, or null if the command is
	 *         not obtain
	 */
	public String process(String command) {

		String[] commandParts = command.split(" ");

		if (commandParts[0].equals("exit") && (commandParts.length == 1)) {
			return null;
		} else if (commandParts[0].equals("obtain")
				&& (commandParts.length == 2)) {

			/*--------- start change ----------*/
			// Checks if the file is valid and present in the peer
			if (peer.hasFile(commandParts[1]) == Peer.FILE_VALID) {
				/*--------- end change ----------*/
				System.out.println("File already exists in your computer.");
				return null;
			}
			System.out.println("Obtaining: " + commandParts[1]);
			return commandParts[1];

			/*--------- start change ----------*/
		} else if (commandParts[0].equals("modify")
				&& (commandParts.length == 2)) {
			System.out.println("Modifying: " + commandParts[1]);
			peer.modifyMasterFile(commandParts[1]);
			return null;

			/*--------- end change ----------*/

		} else if (commandParts[0].equals("")) {
			return null;
		} else {
			System.out.println(HELP);
			return null;
		}
	}

}
