import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;


public class Server {
	private static final String host = "167.205.32.46";
	private static final int port = 5672;
	
	private static final String prefix = "13512084_";
	
	private static final String EXCHANGE_NAME = prefix + "exchange";
	private static final String REQ_QUEUE_NAME = prefix + "req_queue";
	private static final String INIT_QUEUE_NAME = prefix + "init_queue";
	
	private Connection connection;
	private Channel channel;
	private QueueingConsumer consumer;
	
	private List<String> users;
	private List<String> nicks;
	private List<String> channels;
	
	public Server() throws Exception {
		users = new ArrayList<String>();
		nicks = new ArrayList<String>();
		channels = new ArrayList<String>();
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);
		factory.setPort(port);

		connection = factory.newConnection();
		channel = connection.createChannel();
		
		channel.exchangeDeclare(EXCHANGE_NAME, "direct");
		channel.queueDeclare(REQ_QUEUE_NAME, false, false, false, null);
		channel.queueDeclare(INIT_QUEUE_NAME, false, false, false, null);

		channel.basicQos(1);

		consumer = new QueueingConsumer(channel);
		channel.basicConsume(REQ_QUEUE_NAME, false, consumer);
	}
	
	public void start() throws Exception {
		System.out.println("Server is starting ...");
		while (true) {
		    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
		    String request = new String(delivery.getBody());
		    if (!request.isEmpty()){
		    	handleRequest(request, delivery.getProperties().getReplyTo(),
		    			delivery.getEnvelope().getDeliveryTag());
		    }
		}
	}
	
	private void handleRequest(String request, String client, long tag) throws IOException {
		System.out.println("Client " + client + ": " + request);
	    channel.basicAck(tag, false);
		String response = "";
		String[] split = request.split("\\s+");
		switch (split[0]){
			case "/KEY":
				response = key(); 
				client = INIT_QUEUE_NAME;
				break;
			case "/NICK":	
				if (split.length > 1)
					response = nick(client, split[1]); 
				else
					response = nick(client, ""); 
				break;
			case "/JOIN":	
				if (split.length > 1)
					response = join(client, split[1]); 
				else
					response = join(client, ""); 
				break;
			case "/LEAVE":
				response = leave(client, split[1]); break;
			case "/EXIT": 	
				response = exit(client); break;
			default:
				response = sendMessage(client, request); break;
		}
	    channel.basicPublish("", prefix + client, null, response.getBytes());
	}
	
	private String key(){
		SecureRandom random = new SecureRandom();
		String key = new BigInteger(35, random).toString(32);
		users.add(key);
		nicks.add("user" + users.size());
		System.out.println(users.size() + " user(s) online");
		return key;
	}
	
	private String nick(String client, String nick){
		int idx = users.indexOf(client);
		if (!nick.isEmpty()){
			nicks.add(idx, nick);
		}
		nick = nicks.get(idx);
		return "Your nick is " + nick;
	}

	private String join(String client, String channelName){
		if (channelName.isEmpty()){
			channelName = "channel" + channels.size();
		}
		if (!channels.contains(channelName)){
			channels.add(channelName);
		}
		try {
			channel.queueBind(prefix + client, EXCHANGE_NAME, prefix + channelName);
		} catch (IOException e) {
			e.printStackTrace();
			return "Failed to join channel " + channelName;
		}
		return "Join channel " + channelName;
	}
	
	private String leave(String client, String channelName){
		try {
			channel.queueUnbind(prefix + client, EXCHANGE_NAME, prefix + channelName);
		} catch (IOException e) {
			e.printStackTrace();
			return "Failed to leave channel " + channelName;
		}
		return "Leave channel " + channelName;
	}

	private String exit(String client) {
		int idx = users.indexOf(client);
		users.remove(idx);
		nicks.remove(idx);
		return "exited";
	}
	
	private String sendMessage(String client, String message){
		String sender = nicks.get(users.indexOf(client));
		if (message.startsWith("@")){
			String chName = null;
			String msg = null;
			if(message.contains(" ")){
				int idx = message.indexOf(" ");
			    chName = message.substring(1, idx);
			    msg = message.substring(idx+1);
			}
			message = "[" + chName + "] " 
					+ "(" + sender + ") " 
					+ msg;
			try {
				channel.basicPublish(EXCHANGE_NAME, prefix + chName, null, message.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
				return "Failed to send mesage";
			}
		} else {
			for (String chName : channels){
				String msg = "[" + chName + "] " 
						+ "(" + sender + ") " 
						+ message;
				try {
					channel.basicPublish(EXCHANGE_NAME, prefix + chName, null, msg.getBytes());
				} catch (IOException e) {
					e.printStackTrace();
					return "Failed to send mesage";
				}
			}
		}
		return "Delivered";
	}
	
	public void stop() throws Exception {
	    connection.close();
	    System.out.println("Server is stopping ...");
	}

	public static void main(String[] args) {
		Server server;
		try {
			server = new Server();
			server.start();
			server.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
