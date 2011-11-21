/**
 * Timer for clearing the registry
 * 
 * @author palvare3
 * 
 */
public class TimerRestarter extends Thread {

	private PeerBinder peerBinder;

	public TimerRestarter(PeerBinder peerBinder) {
		this.peerBinder = peerBinder;
	}

	@Override
	public void run() {
		super.run();
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
		}
		peerBinder.timeOutTimer();
	}

}
