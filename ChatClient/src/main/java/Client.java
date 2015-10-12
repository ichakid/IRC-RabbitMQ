import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;

public class Client {
	private static final String host = "167.205.32.46";
//	private static final String host = "localhost";
	private static final int port = 5672;
	
	private static final String prefix = "13512084_";
	
	private static final String REQ_QUEUE_NAME = prefix + "req_queue";
	private static final String INIT_QUEUE_NAME = prefix + "init_queue";
	
	private String clientKey;
	private String clientQueue;
	private Connection connection;
	private Channel channel;
	private QueueingConsumer consumer;
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	public Client() throws Exception{
	    ConnectionFactory factory = new ConnectionFactory();
	    factory.setHost(host);
	    factory.setPort(port);
	    connection = factory.newConnection();
	    channel = connection.createChannel();
	    
	    request("/KEY");
	    System.out.println("Connecting to server ...");
	    while(true) {
	    	GetResponse response = channel.basicGet(INIT_QUEUE_NAME, true);
	    	if (response != null){
	    		clientKey = new String(response.getBody());
	    		System.out.println("Connected");
	    		System.out.println("ClientKey is " + clientKey);
	    		break;
	    	}
	    }
	    
	    clientQueue = prefix + clientKey;
	    consumer = new QueueingConsumer(channel);
	    channel.queueDeclare(clientQueue, false, false, true, null);
	    channel.basicConsume(clientQueue, consumer);
	}
	
	public void run() {
		final Thread sender = new Thread(){
			@Override
			public void run() {
				while (running.get()) {
					try {
						Scanner input = new Scanner(System.in);
						String command = input.nextLine();
						request(command);
						Thread.sleep(500);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		final Thread receiver = new Thread(){
			@Override
			public void run() {
				while (running.get()) {
					try {
						receive();
						if (!running.get()) {
							System.out.println("stop");
						}
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
		} finally {
			close();
			System.exit(0);
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
	    		running.set(false);
	    	}
	    	System.out.println(response);
	    }
	    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
	}

	private void close() {
	    try {
	    	channel.queueDelete(clientQueue);
	    	channel.close();
			connection.close();
		    System.out.println("Client stopped");
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
