package com.fenglin.imServer.service;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fenglin.commons.entity.Message;
import com.fenglin.commons.entity.User;
import com.fenglin.commons.utils.JacksonUtils;
import com.fenglin.imServer.IMApplication;
import com.fenglin.imServer.Utils.ThreadManage;
import com.fenglin.tcp.Request;
import com.fenglin.tcp.Response;
import com.fenglin.tcp.SocketUtils;

public class TalkThread extends Thread {

	private Socket socket;
	
	public TalkThread(Socket s, Map<String,List<Message>> Cache) {
		super();
		this.socket = s;
		try {
			Request request = (Request) SocketUtils.readeRequest(s);
			User user = (User) JacksonUtils.json2pojo(request.getToken(), User.class);
			
			Integer  friendId =  (Integer) request.getData() ;
			if ("talklink".equals(request.getPath())) {  
				ThreadManage.put(user.getId()+"-"+friendId,this);
				List<Message> msgCacheList = Cache.get(user.getId()+"-"+friendId);
				if(msgCacheList != null) {
					for (Message m : msgCacheList) {
						Request re = new Request("post", "receiveMsg", request.getToken(), m);
						SocketUtils.farwardRequest(s, re);
					}
					Cache.remove(user.getId()+"-"+friendId);
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	@Override
	public void run() {
		Boolean isColse = true;
		while (isColse) {
			try {
				Request requ = SocketUtils.readeRequest(socket);
				if ("talk".equals(requ.getPath())) { talkDispose(requ);	}
				if ("socketClose".equals(requ.getPath())) { 
					isColse = false;
					int friendId = (int) requ.getData();
					User u = JacksonUtils.json2pojo(requ.getToken(), User.class); 
					ThreadManage.threadMap.remove(u.getId()+"-"+friendId );
					System.out.println("socketClose--->"+ThreadManage.threadMap);
				 }
			} catch (Exception e) { e.printStackTrace(); }
		}
	}
	
	public void talkDispose(Request request) {

		try {
			Message message = (Message) request.getData();
			int friendsId = message.getFirendsId();
			User u = JacksonUtils.json2pojo(request.getToken(), User.class);
			System.out.println("ThreadManage.threadMap--->"+ThreadManage.threadMap);
			Socket friendSocket =  ThreadManage.getfriendSocket(friendsId+"-"+u.getId());
			if(friendSocket != null) {
				System.out.println("消息中转发送朋友--->"+friendsId+"-"+u.getId());
				SocketUtils.farwardRequest(friendSocket, request);
			}else {
				Socket soc = new Socket("localhost",80);
				Request re = new Request("get", "loginCheck", request.getToken(), friendsId, "localhost", 8081);
				SocketUtils.sendRequest(soc, re);
				Response resp = SocketUtils.readeResponse(soc);
				if(resp.getData() != null) {
					System.out.println("用户一登陆了但是没有注册到tklkThread....");
				}else {
					System.out.println("用户没有登陆不在线!将会话信息战时缓存到服务器的临时内存中");
				}
			 
				List<Message> msgCacheList = IMApplication.talkCache.get(friendsId+"-"+u.getId());
				if(msgCacheList == null) msgCacheList = new ArrayList<Message>();
				msgCacheList.add(new Message(message.getMsg(), u.getId()));
				IMApplication.talkCache.put(friendsId+"-"+u.getId(), msgCacheList);
				//@TODO 用户一登陆但是没有和消息中转服务器建立通信连接,将临时会话信息通过对象流写道文件中!待用户登陆后在进行读取
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 

	public Socket getSocket() {
		return socket;
	}

	@Override
	public String toString() {
		return "TalkThread [" + socket + "]";
	}

	 
	
}
