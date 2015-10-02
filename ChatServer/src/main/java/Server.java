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
	private static final String EXCHANGE_NAME = "exchange";
	private static final String REQ_QUEUE_NAME = "req_queue";
	private static final String INIT_QUEUE_NAME = "init_queue";
	
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
		factory.setHost("localhost");

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
	    channel.basicPublish("", client, null, response.getBytes());
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

	private String join(String client, String bindKey) throws IOException{
		if (bindKey.isEmpty()){
			bindKey = "channel" + channels.size();
		}
		if (!channels.contains(bindKey)){
			channels.add(bindKey);
		}
		channel.queueBind(client, EXCHANGE_NAME, bindKey);
		return "Join channel " + bindKey;
	}
	
	private String leave(String client, String bindKey) throws IOException {
		channel.queueUnbind(client, EXCHANGE_NAME, bindKey);
		return "Leave channel " + bindKey;
	}

	private String exit(String client) {
		int idx = users.indexOf(client);
		users.remove(idx);
		nicks.remove(idx);
		return "exited";
	}
	
	private String sendMessage(String client, String message) throws IOException {
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
			channel.basicPublish(EXCHANGE_NAME, chName, null, message.getBytes());
		} else {
			for (String c : channels){
				String msg = "[" + c + "] " 
						+ "(" + sender + ") " 
						+ message;
				channel.basicPublish(EXCHANGE_NAME, c, null, msg.getBytes());
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
