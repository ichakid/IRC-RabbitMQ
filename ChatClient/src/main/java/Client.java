import java.util.Scanner;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;

public class Client {
	private static final String REQ_QUEUE_NAME = "req_queue";
	private static final String INIT_QUEUE_NAME = "init_queue";
	
	private String clientKey;
	private Connection connection;
	private Channel channel;
	private QueueingConsumer consumer;
	public static boolean running;
	
	public Client() throws Exception{
	    ConnectionFactory factory = new ConnectionFactory();
	    factory.setHost("localhost");
	    connection = factory.newConnection();
	    channel = connection.createChannel();
	    
	    request("/KEY");
	    System.out.println("Connecting to server ...");
	    while(true) {
	    	GetResponse response = channel.basicGet(INIT_QUEUE_NAME, false);
	    	if (response != null){
	    		clientKey = new String(response.getBody());
	    		System.out.println("Connected");
	    		System.out.println("ClientKey is " + clientKey);
	    		break;
	    	}
	    }
	    
	    consumer = new QueueingConsumer(channel);
	    channel.queueDeclare(clientKey, false, false, false, null);
	    channel.basicConsume(clientKey, consumer);
	    running = true;
	}
	
	public void run() {
		Thread receiver = new Thread(){
			@Override
			public void run() {
				while (running) {
					try {
						receive();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		Thread sender = new Thread(){
			@Override
			public void run() {
				while (running) {
					try {
						Scanner input = new Scanner(System.in);
						String command = input.nextLine();
						request(command);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		sender.start();
		receiver.start();
		try {
			sender.join();
			receiver.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			close();
		}
	}
	
	// send command to server
	public void request(String message) throws Exception {
	    BasicProperties props = new BasicProperties
	                                .Builder()
	                                .replyTo(clientKey)
	                                .build();

	    channel.basicPublish("", REQ_QUEUE_NAME, props, message.getBytes());
	}
	
	// receive response from server
	public void receive() throws Exception {
		QueueingConsumer.Delivery delivery = consumer.nextDelivery();

	    String response = new String(delivery.getBody());
	    if (!response.isEmpty()){
	    	if(response.equals("exited")){
	    		running = false;
	    	}
	    	System.out.println(response);
	    }
	    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
	}

	private void close() {
	    try {
	    	channel.close();
			connection.close();
		    System.out.println("Client is stopping ...");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Client client;
		try {
			client = new Client();
			client.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
