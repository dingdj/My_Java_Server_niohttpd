package com.seam.core.session;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * provide userful function for session
 * @author dingdongjin
 *
 */
public class SessionUtil {
	
	private static SecureRandom random = new SecureRandom();
	
	private static final int SESSION_IN_BYTES = 16;
	
	public static final int MaxInactiveInterval = 5;
	
	private static Object object = new Object();

	/**
	 * generate unique session id
	 * @return
	 */
	public static String generateSessionID(){
		synchronized(object){
			byte[] bytes = new byte[SESSION_IN_BYTES];
			random.nextBytes(bytes);
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				bytes = messageDigest.digest(bytes);
				return hexEncode(bytes);
			
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			return "";
		}
	}
	
	
	/* The byte[] returned by MessageDigest does not have a nice
	  * textual representation, so some form of encoding is usually performed.
	  *
	  * This implementation follows the example of David Flanagan's book
	  * "Java In A Nutshell", and converts a byte array into a String
	  * of hex characters.
	  *
	  * Another popular alternative is to use a "Base64" encoding.
	  */
	  static private String hexEncode(byte[] aInput){
	    StringBuilder result = new StringBuilder();
	    char[] digits = {'0', '1', '2', '3', '4','5','6','7','8','9','a','b','c','d','e','f'};
	    for ( int idx = 0; idx < aInput.length; ++idx) {
	      byte b = aInput[idx];
	      result.append( digits[ (b&0xf0) >> 4 ] );
	      result.append( digits[ b&0x0f] );
	    }
	    return result.toString();
	  }

}
