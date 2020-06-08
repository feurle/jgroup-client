package com.gwkp.jgroups;

import lombok.extern.slf4j.Slf4j;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.io.*;
import java.text.ParseException;
import java.util.*;

@SpringBootApplication
@Slf4j
public class JGroupsApplication extends ReceiverAdapter implements ApplicationRunner {

    private JChannel channel;
    private String nodeName;
    @Value("${cluster.name}")
    private String clusterName;
    private View lastView;
    private boolean running = true;

    // Our shared state
    private Integer messageCount = 0;

    @PostConstruct
    public void setProperties() {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }


    public static void main(String[] args) {
        log.info("STARTING THE APPLICATION");
        SpringApplication.run(JGroupsApplication.class, args);
        log.info("APPLICATION FINISHED");
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("EXECUTING : command line runner");

        processCommandline(args);

        channel = new JChannel("src/main/resources/udp.xml");

        if (nodeName != null) {
            // name the node
            channel.name(nodeName);
        }

        // Register for callbacks
        channel.setReceiver(this);

        // Ignore out message
        channel.setDiscardOwnMessages(true);

        // connect channel to cluster
        channel.connect(clusterName);

        // Start state transfer
        channel.getState(null, 0);

        // Do the things
        processInput();

        channel.close();
    }


    /**
     * Quick and dirty implementaton of commons cli for command line args
     *
     * @param args the command line args
     * @throws ParseException
     */
    private void processCommandline(ApplicationArguments args) throws ParseException {
        System.out.println("# NonOptionArgs: " + args.getNonOptionArgs().size());

        System.out.println("NonOptionArgs:");
        args.getNonOptionArgs().forEach(System.out::println);

        if (args.getNonOptionArgs().size() == 1) {
            nodeName = args.getNonOptionArgs().get(1);
        }

        System.out.println("# OptionArgs: " + args.getOptionNames().size());
        System.out.println("OptionArgs:");

        args.getOptionNames().forEach(optionName -> {
            System.out.println(optionName + "=" + args.getOptionValues(optionName));
        });
    }

    /**
     * JGroups maintains cluster state inside the View class. Each channel has a single View of the network.
     * When the view changes, it's delivered via the viewAccepted() callback.
     * <p>
     * For this demo, we'll extend the ReceiverAdaptor API class that implements
     * all of the interface methods required for an application.
     * <p>
     * The recommended way is to implement callbacks.
     *
     * @param newView
     */
    @Override
    public void viewAccepted(View newView) {

        // Save view if this is the first
        if (lastView == null) {
            System.out.println("Received initial view:");
            newView.forEach(System.out::println);
        } else {
            // Compare to last view
            System.out.println("Received new view.");

            List<Address> newMembers = View.newMembers(lastView, newView);
            System.out.println("New members: ");
            newMembers.forEach(System.out::println);

            List<Address> exMembers = View.leftMembers(lastView, newView);
            System.out.println("Exited members:");
            exMembers.forEach(System.out::println);
        }
        lastView = newView;
    }


    /**
     * Loop on console input until we see 'x' to exit
     */
    private void processInput() throws Exception {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (running) {
            try {

                // Get a destination, <enter> means broadcast
                Address destination = null;
                System.out.print("Enter a destination: ");
                System.out.flush();
                String destinationName = in.readLine().toLowerCase();

                if (destinationName.equals("x")) {
                    running = false;
                    continue;
                } else if (!destinationName.isEmpty()) {
                    destination = getAddress(destinationName)
                            .orElseThrow(() -> new Exception("Destination not found"));
                }

                // Accept a string to send
                System.out.print("Enter a message: ");
                System.out.flush();
                String line = in.readLine().toLowerCase();
                sendMessage(destination, line);
            } catch (IOException ioe) {
                running = false;
            }
        }
        System.out.println("Exiting.");
    }


    /**
     * Send message from here
     *
     * @param destination   the destination
     * @param messageString the message
     */
    private void sendMessage(Address destination, String messageString) {
        try {
            System.out.println("Sending " + messageString + " to " + destination);
            Message message = new Message(destination, messageString);
            channel.send(message);
        } catch (Exception exception) {
            System.err.println("Exception sending message: " + exception.getMessage());
            running = false;
        }
    }

    @Override
    public void receive(Message message) {
        // Print source and dest with message
        String line = "Message received from: " + message.getSrc() + " to: " + message.getDest() + " -> " + message.getObject();

        // Only track the count of broadcast messages
        // Tracking direct message would make for a pointless state
        if (message.getDest() == null) {
            messageCount++;
            System.out.println("Message count: " + messageCount);
        }

        System.out.println(line);
    }


    @Override
    public void getState(OutputStream output) throws Exception {
        // Serialize into the stream
        Util.objectToStream(messageCount, new DataOutputStream(output));
    }

    @Override
    public void setState(InputStream input) {

        // NOTE: since we know that incrementing the count and transferring the state
        // is done inside the JChannel's thread, we don't have to worry about synchronizing
        // messageCount. For production code it should be synchronized!
        try {
            // Deserialize
            messageCount = Util.objectFromStream(new DataInputStream(input));
        } catch (Exception e) {
            System.out.println("Error deserialing state!");
        }
        System.out.println(messageCount + " is the current messagecount.");
    }


    private Optional<Address> getAddress(String name) {
        View view = channel.view();
        return view.getMembers().stream().filter(address -> name.equals(address.toString())).findFirst();
    }

    private void printProperties() {
        Properties p = System.getProperties();
        Enumeration<Object> keys = p.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = (String) p.get(key);
            log.info(key + ": " + value);
        }
    }
}
