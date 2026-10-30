package mpq;

public class MPQException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1106829951432295418L;

	public MPQException() {
	}

	public MPQException(String arg0) {
		super(arg0);
	}

	// Looking for a file that is not in the archive is routine (map archives are searched before the game data),
	// so that answer is one shared exception without a stack trace; filling in a stack trace for every miss was a
	// noticeable part of starting a game on phones.
	static final MPQException LOOKUP_NOT_FOUND = new MPQException("lookup not found", false);

	private MPQException(String message, boolean writableStackTrace) {
		super(message, null, false, writableStackTrace);
	}

	public MPQException(Throwable arg0) {
		super(arg0);
	}

	public MPQException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public MPQException(String arg0, Throwable arg1, boolean arg2, boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

}
