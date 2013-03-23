package com.seam.core;


import java.util.Properties;

/**
 * 
 * @author dingdongjin
 *
 */
public class ResponseUtil{
	
	public static Response getResponse(Properties parms){
		
		String msg = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\"></head><body><h1>Hello server</h1>\n";
		if ( parms.getProperty("username") == null )
			msg +=
				"<form action='?' method='post'>\n" +
				"  <p>Your name: <input type='text' name='username'></p>\n" +
				"</form>\n";
		else
			msg += "<p>Hello, " + parms.getProperty("username") + "!</p>";

		msg += "</body></html>\n";
		
		return new Response(HttpDef.HTTP_OK, HttpDef.MIME_HTML, msg );
	}

}
