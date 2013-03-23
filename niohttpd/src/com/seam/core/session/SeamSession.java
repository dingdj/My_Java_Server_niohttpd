package com.seam.core.session;

import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

/**
 * the httpsession
 * @author dingdongjin
 *
 */
public class SeamSession extends Hashtable<String, Object> implements HttpSession{
	
	private long creationTime;
	
	private String id;
	
	private long lastAccessedTime;
	
	private int maxInactiveInterval;
	
    private ServletContext servletContext;
    
    private HttpSessionContext httpSessionContext;
    
    private boolean expired;
    
   
    
	@Override
	public Object getAttribute(String attributeName) throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		return this.get(attributeName);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Enumeration getAttributeNames() throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		return this.keys();
	}

	@Override
	public long getCreationTime() {
		// TODO Auto-generated method stub
		return creationTime;
	}

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return id;
	}

	@Override
	public long getLastAccessedTime() {
		// TODO Auto-generated method stub
		return lastAccessedTime;
	}

	@Override
	public int getMaxInactiveInterval() {
		// TODO Auto-generated method stub
		return maxInactiveInterval;
	}

	@Override
	public ServletContext getServletContext() {
		// TODO Auto-generated method stub
		return servletContext;
	}

	@SuppressWarnings("deprecation")
	@Override
	public HttpSessionContext getSessionContext(){
		// TODO Auto-generated method stub
		return httpSessionContext;
	}

	@Override
	public Object getValue(String arg0) throws IllegalStateException{
		// TODO Auto-generated method stub
		return getAttribute(arg0);
	}

	@Override
	public String[] getValueNames() throws IllegalStateException{
		// TODO Auto-generated method stub
		Enumeration e = getAttributeNames();
		Vector names = new Vector();
		while (e.hasMoreElements()) {
			names.addElement(e.nextElement());
		}
		String[] result = new String[names.size()];
		names.copyInto(result);
		return result;
	}

	@Override
	public synchronized void invalidate() throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		Enumeration e = getAttributeNames();
		while (e.hasMoreElements())
		{
			removeAttribute((String) e.nextElement());
		}
		setExpired(true);
	}

	@Override
	public boolean isNew() {
		// TODO Auto-generated method stub
		return lastAccessedTime == 0;
	}

	@Override
	public void putValue(String arg0, Object arg1) throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		this.put(arg0, arg1);
	}

	@Override
	public void removeAttribute(String arg0) throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		this.remove(arg0);
	}

	@Override
	public void removeValue(String name) throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		Object value = remove(name);
	}

	@Override
	public void setAttribute(String arg0, Object arg1) throws IllegalStateException{
		// TODO Auto-generated method stub
		if (expired) {
			throw new IllegalStateException();
		}
		this.put(arg0, arg1);
	}

	@Override
	public void setMaxInactiveInterval(int arg0) {
		// TODO Auto-generated method stub
		this.maxInactiveInterval = arg0;
	}
	
	public boolean checkExpire(){
		return (maxInactiveInterval > 0) && ((System.currentTimeMillis() - this.lastAccessedTime) > (maxInactiveInterval * 1000));
	}

	public HttpSessionContext getHttpSessionContext() {
		return httpSessionContext;
	}

	public void setHttpSessionContext(HttpSessionContext httpSessionContext) {
		this.httpSessionContext = httpSessionContext;
	}

	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
	}

	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	public boolean isExpired() {
		return expired;
	}

	public void setExpired(boolean expired) {
		this.expired = expired;
	}

}
