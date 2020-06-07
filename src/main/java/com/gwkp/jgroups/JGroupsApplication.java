package com.gwkp.jgroups;

import lombok.extern.slf4j.Slf4j;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.text.ParseException;
import java.util.*;

@SpringBootApplication
@Slf4j
public class JGroupsApplication extends ReceiverAdapter implements CommandLineRunner {

	private JChannel channel;

	@Value("${cluster.name}")
	private String clusterName;

	private String nodeName;
	private View lastView;


	@PostConstruct
	public void setProperties() {
		System.setProperty("java.net.preferIPv4Stack","true");
	}


	public static void main(String[] args) {
		log.info("STARTING THE APPLICATION");
		SpringApplication.run(JGroupsApplication.class, args);
		log.info("APPLICATION FINISHED");
	}

	@Override
	public void run(String... args) throws Exception {
		log.info("EXECUTING : command line runner");
		for (int i = 0; i < args.length; ++i) {
			log.info("args[{}]: {}", i, args[i]);
		}
		processCommandLine(args);

		channel = new JChannel("src/main/resources/udp.xml");

		// connect channel to cluster
		channel.connect(clusterName);

		// name the node
		channel.name(nodeName);



		channel.close();
	}

	private void processCommandLine(String[] args) throws ParseException {

	}

	/**
	 * JGroups maintains cluster state inside the View class. Each channel has a single View of the network.
	 * When the view changes, it's delivered via the viewAccepted() callback.
	 *
	 * For this demo, we'll extend the ReceiverAdaptor API class that implements
	 * all of the interface methods required for an application.
	 *
	 * The recommended way is to implement callbacks.
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



	public void printProperties() {
		Properties p = System.getProperties();
		Enumeration<Object> keys = p.keys();
		while (keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			String value = (String) p.get(key);
			log.info(key + ": " + value);
		}
	}
}
