import exceptions.ExitObliged;
import managers.*;

import utilty.*;
import commands.*;

import java.util.List;

public class App extends Thread {
    public static int PORT = 6086;
    public static final int CONNECTION_TIMEOUT = 60 * 1000;
    private static final Printable console = new Console();

    public static void main(String[] args) {
        if(args.length != 0){
            try{
                PORT = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        CollectionManager collectionManager = new CollectionManager();
        FileManager fileManager = new FileManager(console, collectionManager);
        try{
            fileManager.findFile();
            fileManager.createObjects();
        } catch (ExitObliged e){
            console.println(ConsoleColors.toColor("До свидания!", ConsoleColors.YELLOW));
            return;
        }

        CommandManager commandManager = new CommandManager(fileManager);
        commandManager.addCommand(List.of(
                new Help(commandManager),
                new Info(collectionManager),
                new Show(collectionManager),
                new AddElement(collectionManager),
                new Update(collectionManager),
                new RemoveById(collectionManager),
                new Clear(collectionManager),
                new ExecuteScript(),
                new Exit(),
                new AddMinCommand(collectionManager),
                new RemoveGreater(collectionManager),
                new RemoveLower(collectionManager),
                new History(commandManager),
                new RemoveAnyByAge(collectionManager),
                new CountByCharacterCommand(collectionManager),
                new PrintUniqueHeadCommand(collectionManager)
        ));
        RequestHandler requestHandler = new RequestHandler(commandManager);
        Server server = new Server(PORT, requestHandler, fileManager);
        server.run();
    }
}