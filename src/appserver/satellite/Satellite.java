package appserver.satellite;

import appserver.job.Job;
import appserver.comm.ConnectivityInfo;
import appserver.job.UnknownToolException;
import appserver.comm.Message;
import static appserver.comm.MessageTypes.JOB_REQUEST;
import appserver.job.Tool;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Properties;
import utils.PropertyHandler;

/**
 * Class [Satellite] Instances of this class represent computing nodes that execute jobs by
 * calling the callback method of tool a implementation, loading the tool's code dynamically over a network
 * or locally from the cache, if a tool got executed before.
 *
 * @author Dr.-Ing. Wolf-Dieter Otte
 */
public class Satellite extends Thread {

    private ConnectivityInfo satelliteInfo = new ConnectivityInfo();
    private ConnectivityInfo serverInfo = new ConnectivityInfo();
    private HTTPClassLoader classLoader = null;
    private Hashtable<String, Tool> toolsCache = null;

    public Satellite(String satellitePropertiesFile, String classLoaderPropertiesFile, String serverPropertiesFile) {

        // read this satellite's properties and populate satelliteInfo object,
        // which later on will be sent to the server
        try {
            // Initialize the satellite information by reading a property file (specifically Satellite.*.properties)
            // Note that there is no host address for the satellite servers
            Properties properties;
            properties = new PropertyHandler(satellitePropertiesFile);
            satelliteInfo.setPort(Integer.parseInt(properties.getProperty("PORT")));
            satelliteInfo.setName(properties.getProperty("NAME"));

        } catch (Exception e) {
            // The property file could not be read, so exit the program
            System.err.println("Properties file " + satellitePropertiesFile + " not found, exiting ...");
            System.exit(1);
        }

        // read properties of the application server and populate serverInfo object
        // other than satellites, the as doesn't have a human-readable name, so leave it out
        try {
            // Initialize the main server information by reading a property file (specifically WebServer.properties)
            // We don't set a name as there is not 'name' field in the property file of the web server
            Properties properties;
            properties = new PropertyHandler(serverPropertiesFile);
            serverInfo.setHost(properties.getProperty("HOST"));
            serverInfo.setPort(Integer.parseInt(properties.getProperty("PORT")));

        } catch (Exception e) {
            // The property file could not be read, so exit the program
            System.err.println("Properties file " + serverPropertiesFile + " not found, exiting ...");
            System.exit(1);
        }
        
        
        // read properties of the code server and create class loader
        try {
            // Initialize the code server informations by reading a property file (specifically Server.properties)
            // Similarly to the web server, we don't set a name here
            Properties properties;
            properties = new PropertyHandler(classLoaderPropertiesFile);
            String codeServerHost = properties.getProperty("HOST");
            Integer codeServerPort = Integer.valueOf(properties.getProperty("PORT"));
            classLoader = new HTTPClassLoader(codeServerHost, codeServerPort);
        } catch (NumberFormatException nfe) {
            // The port could not be cast into an Integer (check the property file)
            System.err.println("Wrong Portnumber, using Defaults");
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        if (classLoader == null) {
            // The property file could not be read or an exception was raised, so exit the program
            System.err.println("Could not create HTTPClassLoader, exiting ...");
            System.exit(1);
        }

        
        // Create tools cache, so the program can use a previously loaded class directly
        // This cache is filled when requesting a class to the code server, and is thus used when a client connects
        //   and wants to use an already cached class
        this.toolsCache = new Hashtable<>();
        
    }

    @Override
    public void run() {

//         register this satellite with the SatelliteManager on the server
//         ---------------------------------------------------------------
//         ...
        
        
        // create server socket
        ServerSocket serverSocket = null;
        try
        {
            // Create a server socket on the port defined in the properties
            serverSocket = new ServerSocket(satelliteInfo.getPort());
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        if (null == serverSocket)
        {
            // The server socket could not be created, so exit the program
            System.err.println("Could not start the satellitle " + satelliteInfo.getName());
            System.exit(1);
        }


        // start taking job requests in a server loop
        while(true)
        {
            Socket jobSocket = null;
            try
            {
                // Accept new jobs on this satellite server
                jobSocket = serverSocket.accept();
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            if (null != jobSocket)
            {
                // If a job is created (e.g., a socket is connected), then we create a new thread to complete this job
                SatelliteThread jobThread = new SatelliteThread(jobSocket, this);
                jobThread.start();
            }
        }
    }

    // inner helper class that is instanciated in above server loop and processes single job requests
    private class SatelliteThread extends Thread {

        private Satellite satellite = null;
        private Socket jobRequest = null;
        private ObjectInputStream readFromNet = null;
        private ObjectOutputStream writeToNet = null;
        private Message message = null;

        SatelliteThread(Socket jobRequest, Satellite satellite) {
            this.jobRequest = jobRequest;
            this.satellite = satellite;
        }

        @Override
        public void run() {
            // setting up object streams
            try
            {
                this.readFromNet = new ObjectInputStream(this.jobRequest.getInputStream());
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            try
            {
                this.writeToNet = new ObjectOutputStream(this.jobRequest.getOutputStream());
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            if (null == this.readFromNet || null == this.writeToNet)
            {
                // Check that the two streams have been created, and exit the program otherwise
                System.err.println("Satellitle " + satellite.getName() + " could not set its streams");
                System.exit(1);
            }

            // Read a message from the socket, corresponding to a job
            try
            {
                this.message = (Message) readFromNet.readObject();
            } catch (IOException | ClassNotFoundException e)
            {
                e.printStackTrace();
            }

            // If a message is read as expected
            if (null != message)
            {
                // Switch over the different message types
                switch (message.getType())
                {
                    // The message corresponds to a job request
                    case JOB_REQUEST:
                        // processing job request
                        Job job = (Job) message.getContent();
                        try
                        {
                            // Retrieve the tool corresponding to the job
                            Tool tool = getToolObject(job.getToolName());
                            // Process the job
                            Object result = tool.go(job.getParameters());
                            // Send the result to the client
                            writeToNet.writeObject(result);
                        } catch (UnknownToolException | ClassNotFoundException | InstantiationException | IllegalAccessException | IOException e)
                        {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        System.err.println("[SatelliteThread.run] Warning: Message type not implemented");
                }
            }
        }
    }

    /**
     * Aux method to get a tool object, given the fully qualified class string
     * If the tool has been used before, it is returned immediately out of the cache,
     * otherwise it is loaded dynamically
     */
    public Tool getToolObject(String toolClassString) throws UnknownToolException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        Tool toolObject = null;

        // If the requested tool is not in cache yet
        if (null == toolsCache.get(toolClassString))
        {
            System.out.println("\nOperation's Class: " + toolClassString);
            if (null == toolClassString) {
                throw new UnknownToolException();
            }
            // Ask the class loader for this class (requesting the code server, receiving a .class file, etc.)
            Class toolClass = classLoader.loadClass(toolClassString);
            // Create a new instance of the tool
            toolObject = (Tool) toolClass.newInstance();
            // Put this tool into the cache to directly use it the next time
            toolsCache.put(toolClassString, toolObject);
        } else {
            // The tool is already in cache, so just load it
            System.out.println("Operation: \"" + toolClassString + "\" already in Cache");
            toolObject = toolsCache.get(toolClassString);
        }
        
        return toolObject;
    }

    public static void main(String[] args) {
        // start the satellite
        Satellite satellite = new Satellite(args[0], args[1], args[2]);
        satellite.run();
    }
}
