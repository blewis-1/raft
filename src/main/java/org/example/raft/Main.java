package org.example.raft;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import org.example.raft.messages.OrchMessage;
import org.example.raft.messages.RaftMessage;
import org.example.raft.network.Orchestrator;
import org.example.raft.network.Client;
import org.example.raft.network.ClientResponseHandler;

import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Main {
    private static final List<String> COMMANDS = List.of(
            "user1=John",
            "user2=Alice",
            "user3=Bob",
            "user1=null",
            "user4=Charlie",
            "user2=null",
            "user5=David",
            "user3=null",
            "user6=Eve",
            "user4=null");
    private static final Random random = new Random();

    private static void shutdownSystem(ActorSystem<OrchMessage> system) {
        system.tell(new OrchMessage.Stop());
        system.getWhenTerminated().toCompletableFuture().join();
    }

    private static Behavior<RaftMessage> createClient(ActorSystem<OrchMessage> orchestrator) {
        return Behaviors.<RaftMessage>setup(context -> {
            ActorRef<RaftMessage> client = context.spawn(Client.create(orchestrator), "client");
            ActorRef<RaftMessage> responseHandler = context.spawn(ClientResponseHandler.create(orchestrator),
                    "response-handler");
            return Behaviors.receive(RaftMessage.class)
                    .onMessage(RaftMessage.ClientCommand.class, msg -> {
                        client.tell(new RaftMessage.ClientCommand(msg.command(), responseHandler));
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter number of processes:");
        int numberOfProcesses = scanner.nextInt();
        ActorSystem<OrchMessage> orchestrator = ActorSystem.create(Orchestrator.create(numberOfProcesses),
                "orchestrator");
        orchestrator.tell(new OrchMessage.Start(numberOfProcesses));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ActorSystem<RaftMessage> clientSystem = ActorSystem.create(createClient(orchestrator), "client");

        System.out.println("\n Sending random commands every 2 seconds...");
        System.out.println("Press Enter to exit");

        Thread exitThread = new Thread(() -> {
            scanner.nextLine();
            scanner.nextLine();
            System.out.println("Shutting down systems...");
            shutdownSystem(orchestrator);
            clientSystem.terminate();
            clientSystem.getWhenTerminated().toCompletableFuture().join();
            scanner.close();
            System.exit(0);
        });
        exitThread.start();

        boolean running = true;
        while (running) {
            try {
                String command = COMMANDS.get(random.nextInt(COMMANDS.size()));
                System.out.println("Sending command: " + command);
                clientSystem.tell(new RaftMessage.ClientCommand(command, null));
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted, shutting down...");
                running = false;
            }
        }
    }
}
