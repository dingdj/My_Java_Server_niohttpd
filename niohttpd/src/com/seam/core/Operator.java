package com.seam.core;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.seam.core.session.SessionManager;


/**
 * deal with socket read/write
 * @author dingdongjin
 *
 */
public class Operator {
	
	private volatile boolean selectable = true;
	
	private int corePoolSize = 100;
	
	private int maxPoolSize = 200;
	
	private int keepAliveTime  = 5;
	
	private ThreadPoolExecutor executor;
	
	private Selector selector;
	
	private SessionManager sessionManager;
	
	public static BlockingQueue<SocketChannel> socketChannelQueue = new LinkedBlockingQueue<SocketChannel>();
	public static BlockingQueue<KeyState> keyStateQueue = new LinkedBlockingQueue<KeyState>();
	
	/**
	 * start operator
	 * @return
	 */
	public  boolean start(){
		try {
			sessionManager = new SessionManager();
			selector = Selector.open();
			executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, 
											  TimeUnit.SECONDS,
											  new LinkedBlockingQueue<Runnable>(), 
											  new ThreadPoolExecutor.CallerRunsPolicy());
			new Thread(new Worker()).start();
			new Thread(sessionManager).start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * wakup seletor when need to register socketChannel from socketChannelQueue
	 * and change the selectionKey interestOps used by some other thread(execute thread pool)
	 */
	public  void wakeup(){
		selector.wakeup();
	}
    
  	 class Worker implements Runnable{
  		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(selectable){
				
				int nums = 0;
				try {
					nums = selector.select();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				registerSocketChannel();

				changeKeyInterestOps();
				
				if(nums > 0){
					Set<SelectionKey> keys = selector.selectedKeys();
					for (SelectionKey selectionKey : keys) {
						keys.remove(selectionKey);
						if(selectionKey.isReadable()){
							System.out.println("read event receive");
							selectionKey.interestOps(0);
							Handler handler = (Handler) selectionKey.attachment();
							executor.execute(handler);
						}
						
						if(selectionKey.isWritable()){
							System.out.println("write event receive");
							selectionKey.interestOps(0);
							Handler handler = (Handler) selectionKey.attachment();
							executor.execute(handler);
						}
					}
				}
				
			}
		}
		
		/**
		 * regiter client socketchannel in socketChannelQueue
		 */
		private void registerSocketChannel(){
			SocketChannel socketChannel = socketChannelQueue.poll();
			while(socketChannel != null){
				try {
					socketChannel.configureBlocking(false);
					SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
					Handler handler = new Handler(key, sessionManager);
					key.attach(handler);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					if(socketChannel.isOpen()){
						try {
							socketChannel.close();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
				socketChannel = socketChannelQueue.poll();
			}
		}
		
		/**
		 * change key interestOps in keyStateQueue
		 */
		private void changeKeyInterestOps(){
			KeyState keyState = keyStateQueue.poll();
			while(keyState != null){
				keyState.interestChange();
				keyState = keyStateQueue.poll();
			}
		}
    	
    }
    
    
    
   
	 static class KeyState {
		private SelectionKey key;
		private int interestOps;

		public SelectionKey getKey() {
			return key;
		}

		public void setKey(SelectionKey key) {
			this.key = key;
		}

		public int getInterestOps() {
			return interestOps;
		}

		public void setInterestOps(int interestOps) {
			this.interestOps = interestOps;
		}

		public void interestChange() {
			if (key.isValid()) {
				key.interestOps(interestOps);
			}
		}
	}
    
    

}
