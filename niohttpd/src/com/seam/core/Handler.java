package com.seam.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

import com.seam.core.Operator.KeyState;
import com.seam.core.session.SeamSession;
import com.seam.core.session.SessionManager;

/**
 * real worker to parse http request and response
 * @author dingdongjin
 *
 */
public class Handler implements Runnable{
	
	private static int READ_STATE = 1;
	private static int WRITE_STATE = 2;
	
	private SelectionKey key;
	private ByteBuffer byteBuffer = ByteBuffer.allocate(8196);
	private byte[] bytes = new byte[8196]; 
	private int len = 0;
	private int splitbyte = 0;
	private int state = READ_STATE;
	private boolean findHttpHeader = false;
	private HttpHeader httpHeader;
	private static int theBufferSize = 16 * 1024;
	private ByteArrayOutputStream f = new ByteArrayOutputStream();
	private boolean isUnLegalRequest = false;
	private ByteBuffer resByteBuffer;
	private SessionManager sessionManager;
	private SeamSession session;
	
	/**
	 * httprequest body length
	 */
	private long size = 0x7FFFFFFFFFFFFFFFl;
	
	public Handler(SelectionKey key, SessionManager sessionManager){
		this.key = key;
		this.sessionManager = sessionManager;
	}
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		SocketChannel sockChannel = (SocketChannel)key.channel();
		if(sockChannel != null ){
			if(READ_STATE == state){
				int i = 0;
				try{
					 i = sockChannel.read(byteBuffer);
				}catch(IOException e){
					//happened when client is enforced close
					e.printStackTrace();
					clearResource(sockChannel);
					return;
				}
				
				if(i != -1){
					byteBuffer.flip();
					int length = byteBuffer.limit();
					byteBuffer.get(bytes, len, length);
					if(!findHttpHeader){
						//may be null
						httpHeader = getHttpHeader(length);
						if(isUnLegalRequest){
							clearResource(sockChannel);
							return;
						}
					}else{ // get http request body
						parseSession(httpHeader);
						if (size > 0)
						{
							size -= length;
							if (length > 0){
								f.write(bytes, 0, length);
							}
						}
					}
					if(isReadComplete()){ //read all inputstream now deal the message
						handler(sockChannel);
						return;
					}else{                //prepare to next read
						prepareNextRead();
					}
				}else{
					System.out.println("reach inputstream end");
					clearResource(sockChannel);
				}
			}else if(state == WRITE_STATE){ //write operation
					while(resByteBuffer.remaining() > 0){
						int length = 0;
						try {
							length = sockChannel.write(resByteBuffer);
							if (length < 0) {
								resByteBuffer.clear();
						        throw new EOFException();
						    }else if(length == 0){
								prepareWrite();
								return;
							}
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
							clearWriteResource(sockChannel);
						}
						
					}
					//write complete
					clearWriteResource(sockChannel);
				
			}
		}
	}
	
	
	private void clearWriteResource(SocketChannel sockChannel){
		try {
			resByteBuffer.clear();
			sockChannel.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * parse input byte to get httpheader
	 * @param length bytebuffer's limit (after flip) of current read 
	 * @return
	 */
	private HttpHeader getHttpHeader(int length){
		len = len + length;
		splitbyte = findHeaderEnd(bytes, len);
		if(splitbyte > 0){//find http header end
			findHttpHeader = true;
			httpHeader = new HttpHeader();
			// Create a BufferedReader for parsing the header.
			ByteArrayInputStream hbis = new ByteArrayInputStream(bytes, 0, splitbyte);
			BufferedReader hin = new BufferedReader( new InputStreamReader(hbis));
			Properties pre = new Properties();
			Properties parms = new Properties();
			Properties header = new Properties();
			Properties files = new Properties();
			
			// Decode the header into parms and header java properties
			decodeHeader(hin, pre, parms, header);
			httpHeader.setFiles(files);
			httpHeader.setHeader(header);
			httpHeader.setParms(parms);
			httpHeader.setPre(pre);
			
			String contentLength = httpHeader.getHeader().getProperty("content-length");
			if (contentLength != null)
			{
				try { size = Integer.parseInt(contentLength); }
				catch (NumberFormatException ex) {
					//reject the request, is unlegal request
				}
			}
			if (splitbyte < len){
				f.write(bytes, splitbyte, len-splitbyte);
				size = size - (len-splitbyte+1);
				len = 0;//now can reuse bytes
			}else if (splitbyte==0 || size == 0x7FFFFFFFFFFFFFFFl){ //contentLength = 0
				size = 0;
			}
			return httpHeader;
		}else{ //not find http header yet
			//when len >= 8196 and not find http header yet ,the request is unlegal,reject
			if(len > 8196){
				isUnLegalRequest = true;
			}
		}
		return null;
	}
	
	
	/**
	 * get all request bytes
	 * @return
	 */
	private boolean isReadComplete(){
		return !(size > 0);
	}
	
	/**
	 * handle the request
	 * @throws IOException 
	 */
	private void handler(SocketChannel sockChannel){
		// Get the raw body as a byte []
		byte [] fbuf = f.toByteArray();
		
		// Create a BufferedReader for easily reading it as string.
		ByteArrayInputStream bin = new ByteArrayInputStream(fbuf);
		BufferedReader in = new BufferedReader(new InputStreamReader(bin));
		
		
		// If the method is POST, there may be parameters
		// in data section, too, read it:
		String method = httpHeader.getPre().getProperty("method");
		if (method.equalsIgnoreCase( "POST" ))
		{
			String contentType = "";
			String contentTypeHeader = httpHeader.getHeader().getProperty("content-type");
			StringTokenizer st = new StringTokenizer( contentTypeHeader , "; " );
			if ( st.hasMoreTokens()) {
				contentType = st.nextToken();
			}

			if (contentType.equalsIgnoreCase("multipart/form-data"))
			{
				// Handle multipart/form-data
				if ( !st.hasMoreTokens())
					System.out.println("BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html" );
				String boundaryExp = st.nextToken();
				st = new StringTokenizer( boundaryExp , "=" );
				if (st.countTokens() != 2)
					System.out.println("BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html" );
				st.nextToken();
				String boundary = st.nextToken();
				decodeMultipartData(boundary, fbuf, in, httpHeader.getParms(), httpHeader.getFiles());
			}
			else
			{
				// Handle application/x-www-form-urlencoded
				String postLine = "";
				char pbuf[] = new char[512];
				int read;
				try {
					read = in.read(pbuf);
					while ( read >= 0 && !postLine.endsWith("\r\n") )
					{
						postLine += String.valueOf(pbuf, 0, read);
						read = in.read(pbuf);
					}
					postLine = postLine.trim();
					postLine = URLDecoder.decode(postLine, "UTF-8");
					decodeParms( postLine, httpHeader.getParms());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		
		if (method.equalsIgnoreCase("PUT"))
			httpHeader.getFiles().put("content", saveTmpFile(fbuf, 0, f.size()));
		
		Response r = getResponse();
		resByteBuffer = sendResponse(r.status, r.mimeType, r.header, r.text);
		state = WRITE_STATE;
		prepareWrite();
	}
	
	/**
	 * prepare for next read
	 */
	private void prepareNextRead(){
		byteBuffer.clear();
		KeyState keystate = new KeyState();
		keystate.setInterestOps(SelectionKey.OP_READ);
		keystate.setKey(key);
		Operator.keyStateQueue.add(keystate);
		key.selector().wakeup();
	}
	
	
	/**
	 * prepare for next read
	 */
	private void prepareWrite(){
		KeyState keystate = new KeyState();
		keystate.setInterestOps(SelectionKey.OP_WRITE);
		keystate.setKey(key);
		Operator.keyStateQueue.add(keystate);
		key.selector().wakeup();
	}
	
	/**
	 * clear resource when read complete
	 */
	private void clearResource(SocketChannel sockChannel){
		byteBuffer.clear();
		try {
			sockChannel.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Find byte index separating header from body.
	 * It must be the last byte of the first two sequential new lines.
	**/
	private int findHeaderEnd(final byte[] buf, int limit)
	{
		int splitbyte = 0;
		while (splitbyte + 3 < limit)
		{
			if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n')
				return splitbyte + 4;
			splitbyte++;
		}
		return 0;
	}
	
	
	/**
	 * Decodes the sent headers and loads the data into
	 * java Properties' key - value pairs
	**/
	private void decodeHeader(BufferedReader in, Properties pre, Properties parms, Properties header)
	{
		try {
			// Read the request line
			String inLine = in.readLine();
			if (inLine == null) return;
			inLine = URLDecoder.decode(inLine, "UTF-8");
			StringTokenizer st = new StringTokenizer( inLine );
			if ( !st.hasMoreTokens())
				System.out.println("BAD REQUEST: Syntax error. Usage: GET /example/file.html" );

			String method = st.nextToken();
			pre.put("method", method);

			if ( !st.hasMoreTokens())
				System.out.println("BAD REQUEST: Missing URI. Usage: GET /example/file.html" );

			String uri = st.nextToken();

			// Decode parameters from the URI
			int qmi = uri.indexOf( '?' );
			if ( qmi >= 0 )
			{
				decodeParms( uri.substring( qmi+1 ), parms );
				uri = decodePercent( uri.substring( 0, qmi ));
			}
			else uri = decodePercent(uri);

			// If there's another token, it's protocol version,
			// followed by HTTP headers. Ignore version but parse headers.
			// NOTE: this now forces header names lowercase since they are
			// case insensitive and vary by client.
			if ( st.hasMoreTokens())
			{
				String line = in.readLine();
				while ( line != null && line.trim().length() > 0 )
				{
					int p = line.indexOf( ':' );
					if ( p >= 0 )
						header.put( line.substring(0,p).trim().toLowerCase(), line.substring(p+1).trim());
					line = in.readLine();
				}
			}

			pre.put("uri", uri);
		}
		catch ( IOException ioe )
		{
			System.out.println("SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
		}
	}
	
	
	
	/**
	 * Decodes parameters in percent-encoded URI-format
	 * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
	 * adds them to given Properties. NOTE: this doesn't support multiple
	 * identical keys due to the simplicity of Properties -- if you need multiples,
	 * you might want to replace the Properties with a Hashtable of Vectors or such.
	 */
	private void decodeParms( String parms, Properties p )
	{
		if ( parms == null )
			return;

		StringTokenizer st = new StringTokenizer( parms, "&" );
		while ( st.hasMoreTokens())
		{
			String e = st.nextToken();
			int sep = e.indexOf( '=' );
			if ( sep >= 0 )
				p.put( decodePercent( e.substring( 0, sep )).trim(),
					   decodePercent( e.substring( sep+1 )));
		}
	}
	
	/**
	 * Decodes the percent encoding scheme. <br/>
	 * For example: "an+example%20string" -> "an example string"
	 */
	private String decodePercent(String str)
	{
		try
		{
			StringBuffer sb = new StringBuffer();
			for( int i=0; i<str.length(); i++ )
			{
				char c = str.charAt( i );
				switch ( c )
				{
					case '+':
						sb.append( ' ' );
						break;
					case '%':
						sb.append((char)Integer.parseInt( str.substring(i+1,i+3), 16 ));
						i += 2;
						break;
					default:
						sb.append( c );
						break;
				}
			}
			return sb.toString();
		}
		catch( Exception e )
		{
			System.out.println( "BAD REQUEST: Bad percent-encoding." );
			return null;
		}
	}
	
	
	/**
	 * Decodes the Multipart Body data and put it
	 * into java Properties' key - value pairs.
	**/
	private void decodeMultipartData(String boundary, byte[] fbuf, BufferedReader in, Properties parms, Properties files)
	{
		try
		{
			int[] bpositions = getBoundaryPositions(fbuf,boundary.getBytes());
			int boundarycount = 1;
			String mpline = in.readLine();
			while ( mpline != null )
			{
				if (mpline.indexOf(boundary) == -1)
					System.out.println("BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html" );
				boundarycount++;
				Properties item = new Properties();
				mpline = in.readLine();
				while (mpline != null && mpline.trim().length() > 0)
				{
					int p = mpline.indexOf( ':' );
					if (p != -1)
						item.put( mpline.substring(0,p).trim().toLowerCase(), mpline.substring(p+1).trim());
					mpline = in.readLine();
				}
				if (mpline != null)
				{
					String contentDisposition = item.getProperty("content-disposition");
					if (contentDisposition == null)
					{
						System.out.println("BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html" );
					}
					StringTokenizer st = new StringTokenizer( contentDisposition , "; " );
					Properties disposition = new Properties();
					while ( st.hasMoreTokens())
					{
						String token = st.nextToken();
						int p = token.indexOf( '=' );
						if (p!=-1)
							disposition.put( token.substring(0,p).trim().toLowerCase(), token.substring(p+1).trim());
					}
					String pname = disposition.getProperty("name");
					pname = pname.substring(1,pname.length()-1);

					String value = "";
					if (item.getProperty("content-type") == null) {
						while (mpline != null && mpline.indexOf(boundary) == -1)
						{
							mpline = in.readLine();
							if ( mpline != null)
							{
								int d = mpline.indexOf(boundary);
								if (d == -1)
									value+=mpline;
								else
									value+=mpline.substring(0,d-2);
							}
						}
					}
					else
					{
						if (boundarycount> bpositions.length)
							System.out.println("Error processing request" );
						int offset = stripMultipartHeaders(fbuf, bpositions[boundarycount-2]);
						String path = saveTmpFile(fbuf, offset, bpositions[boundarycount-1]-offset-4);
						files.put(pname, path);
						value = disposition.getProperty("filename");
						value = value.substring(1,value.length()-1);
						do {
							mpline = in.readLine();
						} while (mpline != null && mpline.indexOf(boundary) == -1);
					}
					parms.put(pname, value);
				}
			}
		}
		catch ( IOException ioe )
		{
			System.out.println("SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
		}
	}
	
	
	/**
	 * It returns the offset separating multipart file headers
	 * from the file's data.
	**/
	private int stripMultipartHeaders(byte[] b, int offset)
	{
		int i = 0;
		for (i=offset; i<b.length; i++)
		{
			if (b[i] == '\r' && b[++i] == '\n' && b[++i] == '\r' && b[++i] == '\n')
				break;
		}
		return i+1;
	}
	
	
	/**
	 * Retrieves the content of a sent file and saves it
	 * to a temporary file.
	 * The full path to the saved file is returned.
	**/
	private String saveTmpFile(byte[] b, int offset, int len)
	{
		String path = "";
		if (len > 0)
		{
			String tmpdir = System.getProperty("java.io.tmpdir");
			try {
				File temp = File.createTempFile("NanoHTTPD", "", new File(tmpdir));
				OutputStream fstream = new FileOutputStream(temp);
				fstream.write(b, offset, len);
				fstream.close();
				path = temp.getAbsolutePath();
			} catch (Exception e) { // Catch exception if any
				System.out.println("Error: " + e.getMessage());
			}
		}
		return path;
	}
	
	
	/**
	 * Find the byte positions where multipart boundaries start.
	**/
	public int[] getBoundaryPositions(byte[] b, byte[] boundary)
	{
		int matchcount = 0;
		int matchbyte = -1;
		Vector matchbytes = new Vector();
		for (int i=0; i<b.length; i++)
		{
			if (b[i] == boundary[matchcount])
			{
				if (matchcount == 0)
					matchbyte = i;
				matchcount++;
				if (matchcount==boundary.length)
				{
					matchbytes.addElement(new Integer(matchbyte));
					matchcount = 0;
					matchbyte = -1;
				}
			}
			else
			{
				i -= matchcount;
				matchcount = 0;
				matchbyte = -1;
			}
		}
		int[] ret = new int[matchbytes.size()];
		for (int i=0; i < ret.length; i++)
		{
			ret[i] = ((Integer)matchbytes.elementAt(i)).intValue();
		}
		return ret;
	}
	
	
	/**
	 * Sends given response to the socket.
	 */
	private ByteBuffer sendResponse( String status, String mime, Properties header, String response )
	{
		
			if ( status == null )
				throw new Error( "sendResponse(): Status can't be null." );

			StringBuffer buffer = new StringBuffer();
			buffer.append("HTTP/1.0 " + status + " \r\n");
			if ( mime != null )
				buffer.append("Content-Type: " + mime + "\r\n");

			SimpleDateFormat gmtFrmt = new java.text.SimpleDateFormat( "E, d MMM yyyy HH:mm:ss 'GMT'", Locale.CHINA);
			gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
			
			if ( header == null || header.getProperty( "Date" ) == null )
				buffer.append( "Date: " + gmtFrmt.format( new Date()) + "\r\n");

			if ( header != null )
			{
				Enumeration e = header.keys();
				while ( e.hasMoreElements())
				{
					String key = (String)e.nextElement();
					String value = header.getProperty( key );
					buffer.append( key + ": " + value + "\r\n");
				}
			}

			buffer.append("\r\n");

			if ( response != null )
			{
				buffer.append(response);
			}
			
			try {
				byte[] bytes = buffer.toString().getBytes("UTF-8");
				ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
				return byteBuffer;
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
	}
	
	/**
	 * override this method to supply your own Response;
	 * @return Response
	 */
	public Response getResponse(){
		Response r = ResponseUtil.getResponse(httpHeader.getParms());
		return r;
	}
	
	/**
	 * find jSessionId in httpheader if not found add new session
	 * and return jSessionId in response
	 */
	private void parseSession(HttpHeader httpHeader){
		Properties header = httpHeader.getHeader();
		if(header.containsKey("Cookie")){
			String sessionId = header.getProperty("Cookie");
			int sessionStatus = sessionManager.getSessionStatus(sessionId);
			if(SessionManager.SESSION_NOT_EXIST == sessionStatus){
				//session = sessionManager.createSession();
			}else if(SessionManager.SESSION_EXIST == sessionStatus){
				//session = sessionManager.getSessionById(sessionId);
			}else if(SessionManager.SESSION_EXPIRE == sessionStatus){
				//send response with error session is expire
			}
		}else{
			//session = sessionManager.createSession();
		}
	}
	
	class HttpHeader{
		Properties pre;
		Properties parms;
		Properties header;
		Properties files;
		
		public Properties getPre() {
			return pre;
		}
		public void setPre(Properties pre) {
			this.pre = pre;
		}
		public Properties getParms() {
			return parms;
		}
		public void setParms(Properties parms) {
			this.parms = parms;
		}
		public Properties getHeader() {
			return header;
		}
		public void setHeader(Properties header) {
			this.header = header;
		}
		public Properties getFiles() {
			return files;
		}
		public void setFiles(Properties files) {
			this.files = files;
		}
		
		
	}

}
