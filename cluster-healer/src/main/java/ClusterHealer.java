import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ClusterHealer implements Watcher {

    // Define variables and constants

    // Zookeeper server we want to connect to
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    // If the client does not get a server signal after 3 sec, it will think the server is dead
    private static final int SESSION_TIMEOUT = 3000;
    // parent znode
    public static final String PARENT_NAMESPACE = "/workers";
    // Path to the worker jar
    private String pathToProgram;
    // The number of worker instances we need to maintain at all times
    private int numberOfWorkers;
    // store an instance of the Zookeeper
    private ZooKeeper zooKeeper;

    public ClusterHealer(int numberOfWorkers, String pathToProgram) {
        // Initialises the instance variables
        this.numberOfWorkers = numberOfWorkers;
        this.pathToProgram = pathToProgram;
    }

    /**
     * Check if the `/workers` parent znode exists, and create it if it doesn't. Decide for yourself what type of znode
     * it should be (e.g.persistent, ephemeral etc.). Check if workers need to be launched.
     */
    public void initialiseCluster() throws InterruptedException, KeeperException, IOException {
        // checks for the existence of the znode
        Stat s = zooKeeper.exists(PARENT_NAMESPACE, this);
        // if parent znode exists then
        if (s != null){
            // print the message and
            System.out.println("Parent znode exists");
        } else {
            // if it doesn't exist, create it
            // persistent - persists between sessions
            zooKeeper.create(PARENT_NAMESPACE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        // calls checkRunningWorkers method
        checkRunningWorkers();
    }

    /**
     * Instantiates a Zookeeper client, creating a connection to the Zookeeper server.
     */
    public void connectToZookeeper() throws IOException {
        // creating new connection to ZooKeeper
        // requires address of the zookeeper, session timeout and watcher
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    /**
     * Keeps the application running waiting for Zookeeper events.
     */
    public void run() throws InterruptedException {
        // creating synchronized block
        // only one thread can access this at a time
        synchronized (zooKeeper){
            // wait until another thread notifies this object that it's okay to continue
            zooKeeper.wait();
        }
    }

    /**
     * Closes the Zookeeper client connection.
     */
    public void close() throws InterruptedException {
        // close the ZooKeeper client connection
        zooKeeper.close();
    }

    /**
     * Handles Zookeeper events related to: - Connecting and disconnecting from the Zookeeper server. - Changes in the
     * number of workers currently running.
     *
     * @param event A Zookeeper event
     */
    public void process(WatchedEvent event) {
        // Handling Zookeeper events
        switch (event.getType()){
            // for connected and disconnected
            case None:
                // Connecting to the Zookeeper server
                if (event.getState() == Event.KeeperState.SyncConnected){
                    System.out.println("Successfully connected to Zookeeper");
                } else {
                    // Disconnecting from the Zookeeper server
                    synchronized (zooKeeper){
                        System.out.println("Disconnected from Zookeeper");
                        // notifies any other thread that are using this lock
                        zooKeeper.notifyAll();
                    }
                }
                break;
            // event handler - handles the node children changed
            // if the node that we are watching gets changed,
            // then checkRunningWorkers method gets called
            case NodeChildrenChanged:
                // calls checkRunningWorkers method
                checkRunningWorkers();
        }
    }

    /**
     * Checks how many workers are currently running.
     * If less than the required number, then start a new worker.
     */
    public void checkRunningWorkers(){
        try {
            // list of workers
            // checks what are the current children of the parent znode
            List<String> children = zooKeeper.getChildren(PARENT_NAMESPACE, this);
            // checks how many workers are currently working
            // prints message with the number of workers running
            System.out.println("Currently there are " + children.size() + " workers");
            // if less than the required number of workers, start a new worker
            if (children.size() < numberOfWorkers){
                startWorker();
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
        e.printStackTrace();
    }
    }

    /**
     * Starts a new worker using the path provided as a command line parameter.
     *
     * @throws IOException
     */
    public void startWorker() throws IOException {
        File file = new File(pathToProgram);
        String command = "java -jar " + file.getName();
        System.out.println(String.format("Launching worker instance : %s ", command));
        Runtime.getRuntime().exec(command, null, file.getParentFile());
    }
}
