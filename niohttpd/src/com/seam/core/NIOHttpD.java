package com.seam.core;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.seam.core.session.SeamSession;
import com.seam.core.session.SessionManager;

/**
 * 
 * @author dingdongjin
 *
 */
public class NIOHttpD {
	
	private int tcpPort;
	private File rootDir;
	private boolean running = false;
	private Operator operator;
	
	
	public NIOHttpD( int port, File wwwroot ){
		this.tcpPort = port;
		this.rootDir = wwwroot;
		this.operator = new Operator();
	}
	/**
	 * Starts a HTTP server to given port.<p>
	 * Throws an IOException if the socket is already in use
	 * @throws InterruptedException 
	 */
	public void startServer(){
			Selector selector;
			//open server channel
			ServerSocketChannel channel = null;
			SelectionKey key = null;
			try {
				selector = Selector.open();
				channel = ServerSocketChannel.open();
				ServerSocket socket = channel.socket();
				socket.bind(new InetSocketAddress(tcpPort));
				channel.configureBlocking(false);
				key = channel.register(selector, SelectionKey.OP_ACCEPT);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				clearChannel(channel);
				return;
			}
			running = true;
			//start operator
			if(!operator.start()){
				System.out.println("start server failed, because start operator failed");
				clearChannel(channel);
				running = false;
				return;
			}
			
			while(running){
				int num = 0;
				try {
					num = selector.select();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(num > 0){
					//get client connet channel
					SocketChannel clientChannel;
					try {
						selector.selectedKeys().remove(key);
						clientChannel = channel.accept();
						Operator.socketChannelQueue.add(clientChannel);
						operator.wakeup();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			//clear resource
			if(!running){
				clearChannel(channel);
			}
		
	}
	
	/**
	 * stop server
	 */
	public void stopServer(){
		running = false;
	}
	
	private void clearChannel(ServerSocketChannel channel){
		if(channel != null && channel.isOpen()){
			try {
				channel.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
	
	
	public static void main(String[] args){
		NIOHttpD httpserver = new NIOHttpD(10005, new File("d:\\"));
		httpserver.startServer();
		
	}

}
