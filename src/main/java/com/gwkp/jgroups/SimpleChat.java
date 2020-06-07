package com.gwkp.jgroups;

import org.jgroups.JChannel;

import javax.annotation.PostConstruct;

public class SimpleChat {
    JChannel channel;
    String user_name=System.getProperty("user.name", "n/a");



    private void start() throws Exception {
        channel=new JChannel(); // use the default config, udp.xml
        channel.connect("ChatCluster");
    }

    public static void main(String[] args) throws Exception {
        new SimpleChat().start();
    }
}