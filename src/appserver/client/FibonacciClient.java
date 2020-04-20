package appserver.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Properties;

import appserver.comm.Message;
import appserver.comm.MessageTypes;
import appserver.job.Job;
import utils.PropertyHandler;

public class FibonacciClient implements MessageTypes{
	
	String host = null;
    int port;
    Integer number;

    Properties properties;
    
    public FibonacciClient(String serverPropertiesFile, int i) {
        try {
            properties = new PropertyHandler(serverPropertiesFile);
            host = properties.getProperty("HOST");
            System.out.println("[PlusOneClient.PlusOneClient] Host: " + host);
            port = Integer.parseInt(properties.getProperty("PORT"));
            System.out.println("[PlusOneClient.PlusOneClient] Port: " + port);
            number = new Integer(i);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    
    public void run() {
        try { 
            // connect to application server
            Socket server = new Socket(host, port);
            
            // hard-coded string of class, aka tool name ... plus one argument
            String classString = "appserver.job.impl.Fibonacci";
            
            // create job and job request message
            Job job = new Job(classString, number);
            Message message = new Message(JOB_REQUEST, job);
            
            // sending job out to the application server in a message
            ObjectOutputStream writeToNet = new ObjectOutputStream(server.getOutputStream());
            writeToNet.writeObject(message);
            
            // reading result back in from application server
            // for simplicity, the result is not encapsulated in a message
            ObjectInputStream readFromNet = new ObjectInputStream(server.getInputStream());
            Integer result = (Integer) readFromNet.readObject();
            System.out.println("RESULT: " + result);
        } catch (Exception ex) {
            System.err.println("[PlusOneClient.run] Error occurred");
            ex.printStackTrace();
        }
    }
    
    

	public static void main(String[] args) {
		
		FibonacciClient client = null;
        
			for (int i = 48; i > 0; i--) {
				
				client = new FibonacciClient("../../../config/Server.properties",i);
				client.run();
			}
            
       
	}

}
