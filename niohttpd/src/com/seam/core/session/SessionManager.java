package com.seam.core.session;

import java.nio.channels.SocketChannel;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * manager sessions
 * @author dingdongjin
 *
 */
public class SessionManager implements Runnable{
	
	public static final int SESSION_EXIST = 1;
	public static final int SESSION_EXPIRE = 2;
	public static final int SESSION_NOT_EXIST = 0;
	
	private volatile boolean isRunning = true;
	
	private Map<String, SeamSession> sessionPool = new ConcurrentHashMap<String, SeamSession>();
	
	
	public synchronized SeamSession createSession(){
		SeamSession session = new SeamSession();
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(SessionUtil.MaxInactiveInterval);
		String sessionId = SessionUtil.generateSessionID();
		while(sessionPool.get(sessionId) != null){
			sessionId = SessionUtil.generateSessionID();
		}
		session.setId(sessionId);
		session.setExpired(false);
		sessionPool.put(sessionId, session);
		return session;
	}
	
	/**
	 * test SessionId is exist
	 * @return 1:exist,2:expire,0:not exist 
	 */
	public int getSessionStatus(String sessionId){
		SeamSession session = sessionPool.get(sessionId);
		if(session == null){
			return SESSION_NOT_EXIST;
		}else{
			if(session.checkExpire()){
				return SESSION_EXPIRE;
			}
			return SESSION_EXIST;
		}
	}
	
	/**
	 * check the session status before call the function
	 * @param sessionId
	 * @return
	 */
	public SeamSession getSessionById(String sessionId){
		return sessionPool.get(sessionId);
	}
	
	
	public void removeSession(SeamSession session){
		sessionPool.remove(session.getId());
	}

    /**
     * check the expired session and remove it by 1 minute
     */
	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(isRunning){
			Iterator iterator = sessionPool.keySet().iterator();
			while(iterator.hasNext()){
				String sessionId = (String) iterator.next();
				SeamSession session = sessionPool.get(sessionId);
				if(session.checkExpire()){
					session.invalidate();
					removeSession(session);
				}
			}
			try {
				Thread.sleep(1000*60);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				//do nothing
			}
		}
		
	}


	public boolean isRunning() {
		return isRunning;
	}


	public void setRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}
}
